package com.ruralmarket.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 食品投诉关联 + 留样送检 + 同批次管控 + 结果联动 + 抽签资格限制 全链路集成测试（H2 内存库）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:mkt;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FoodSafetyFlowTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    private final ObjectMapper json = new ObjectMapper();

    private String base() { return "http://localhost:" + port + "/api"; }

    private String login(String username, String password) {
        ResponseEntity<String> r = rest.postForEntity(base() + "/auth/login",
                body(java.util.Map.of("username", username, "password", password)), String.class);
        assertEquals(200, r.getStatusCode().value(), "登录应成功: " + r.getBody());
        return read(r.getBody()).get("token").asText();
    }

    private static HttpEntity<String> body(Object obj) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            return new HttpEntity<>(new ObjectMapper().writeValueAsString(obj), h);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private HttpEntity<String> authed(String token, Object obj) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            h.setBearerAuth(token);
            return new HttpEntity<>(new ObjectMapper().writeValueAsString(obj), h);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private HttpEntity<Void> authedGet(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }

    private JsonNode read(String s) {
        try { return json.readTree(s); } catch (Exception e) { throw new RuntimeException(e); }
    }

    private JsonNode post(String token, String path, Object req) {
        ResponseEntity<String> r = rest.exchange(base() + path, HttpMethod.POST, authed(token, req), String.class);
        assertTrue(r.getStatusCode().is2xxSuccessful(), "POST " + path + " 失败: " + r.getStatusCode() + " " + r.getBody());
        return read(r.getBody());
    }

    private JsonNode get(String token, String path) {
        ResponseEntity<String> r = rest.exchange(base() + path, HttpMethod.GET, authedGet(token), String.class);
        assertTrue(r.getStatusCode().is2xxSuccessful(), "GET " + path + " 失败: " + r.getStatusCode() + " " + r.getBody());
        return read(r.getBody());
    }

    private JsonNode postExpectFail(String token, String path, Object req) {
        ResponseEntity<String> r = rest.exchange(base() + path, HttpMethod.POST, authed(token, req), String.class);
        assertEquals(400, r.getStatusCode().value(), "应被业务规则拒绝: " + r.getBody());
        return read(r.getBody());
    }

    private long publicComplaint(Object req) {
        ResponseEntity<String> r = rest.postForEntity(base() + "/complaints/public", body(req), String.class);
        assertEquals(200, r.getStatusCode().value(), "公开投诉应成功: " + r.getBody());
        return read(r.getBody()).get("complaintId").asLong();
    }

    private long createOpenEvent(String admin, String name, LocalDate date) {
        JsonNode e = post(admin, "/admin/events", java.util.Map.of("name", name, "marketDate", date.toString()));
        long id = e.get("id").asLong();
        post(admin, "/admin/events/" + id + "/status", java.util.Map.of("status", "OPEN"));
        return id;
    }

    private void changeStatus(String admin, long id, String s) {
        post(admin, "/admin/events/" + id + "/status", java.util.Map.of("status", s));
    }

    private long applyAndApprove(String vendor, String admin, long eventId, String category,
                                 boolean cooked, boolean power) {
        JsonNode app = post(vendor, "/vendor/applications", java.util.Map.of(
                "eventId", eventId, "category", category,
                "sellsCookedFood", cooked, "sellsLivePoultry", false, "needsPower", power));
        long appId = app.get("id").asLong();
        post(admin, "/admin/applications/" + appId + "/review", java.util.Map.of("approve", true, "note", "通过"));
        return appId;
    }

    private String stallNoOfVendor(String vendor, long eventId) {
        JsonNode results = get(vendor, "/vendor/results");
        for (JsonNode r : results) {
            if (r.get("application").get("event").get("id").asLong() == eventId) {
                return r.get("stallNo").asText();
            }
        }
        throw new IllegalStateException("未找到中签结果");
    }

    private void payAll(String vendor) {
        JsonNode pays = get(vendor, "/vendor/payments");
        for (JsonNode p : pays) {
            if ("UNPAID".equals(p.get("status").asText())) {
                post(vendor, "/vendor/payments/" + p.get("id").asLong() + "/pay", java.util.Map.of());
            }
        }
    }

    // ================= 场景一：腹泻投诉 → 送检 → 不合格联动 → 抽签资格受限 =================

    @Test
    @Order(1)
    void unqualifiedFlow_blocksLaterCookedLottery() {
        LocalDate today = LocalDate.now();
        String admin = login("admin", "admin123");
        String vendor6 = login("vendor6", "vendor123"); // 孙六 凉菜 老摊主
        String vendor4 = login("vendor4", "vendor123"); // 赵四 果蔬

        long eventId = createOpenEvent(admin, "测试集-当期", today.plusDays(5));
        applyAndApprove(vendor6, admin, eventId, "COLD_DISH", true, true);
        changeStatus(admin, eventId, "CLOSED");
        JsonNode run = post(admin, "/admin/events/" + eventId + "/lottery",
                java.util.Map.of("seed", 20261115L));
        assertTrue(run.get("remark").asText().contains("中签 1"), run.get("remark").asText());

        payAll(vendor6);
        String stallNo = stallNoOfVendor(vendor6, eventId);

        // 摊主上传凉拌菜留样
        JsonNode sample = post(vendor6, "/vendor/samples", java.util.Map.of(
                "eventId", eventId, "productName", "凉拌黄瓜", "batchNo", "20261115-01",
                "photo", "/files/demo.jpg", "storageLocation", "1号冷藏柜第2层"));
        long sampleId = sample.get("id").asLong();

        // 两名消费者免登录投诉（购买时间、支付凭证、批次），用于同批次关联
        long complaintId = publicComplaint(java.util.Map.of(
                "eventId", eventId, "stallNo", stallNo, "reporterName", "张三",
                "reporterPhone", "13700000001", "type", "DIARRHEA",
                "description", "食用凉拌黄瓜后当晚腹泻", "foodName", "凉拌黄瓜",
                "batchNo", "20261115-01", "purchaseTime", today.plusDays(5).atTime(19, 30).toString(),
                "paymentProof", "/files/pay1.jpg"));
        publicComplaint(java.util.Map.of(
                "eventId", eventId, "stallNo", stallNo, "reporterName", "李四",
                "reporterPhone", "13700000002", "type", "DIARRHEA",
                "description", "同吃一份凉拌菜，腹泻", "foodName", "凉拌黄瓜",
                "batchNo", "20261115-01", "purchaseTime", today.plusDays(5).atTime(20, 0).toString()));

        // 案件关联聚合
        JsonNode caseBundle = get(admin, "/admin/complaints/" + complaintId + "/case");
        assertEquals(1, caseBundle.get("samples").size(), "应关联到摊主留样");
        assertEquals(1, caseBundle.get("relatedConsumers").size(), "应关联出另一名同批次消费者");
        assertTrue(caseBundle.get("payments").size() >= 3, "应有关联缴费记录");

        // 未登录访问管理接口 403
        ResponseEntity<String> forbidden = rest.exchange(base() + "/admin/lab-tests",
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        assertEquals(403, forbidden.getStatusCode().value());

        // 发起送检：要求立即下架，调查期间暂停熟食售卖 3 天
        JsonNode lab = post(admin, "/admin/lab-tests", java.util.Map.of(
                "complaintId", complaintId, "sampleId", sampleId,
                "productName", "凉拌黄瓜", "batchNo", "20261115-01",
                "labName", "县检验检测中心", "testItems", "菌落总数,大肠菌群,沙门氏菌",
                "batchControl", "REMOVE", "suspendNow", true, "suspendDays", 3));
        long labId = lab.get("id").asLong();
        assertEquals("INITIATED", lab.get("status").asText());

        // 摊主收到待处理管控
        JsonNode pending = get(vendor6, "/vendor/batch-controls");
        assertEquals(1, pending.size());
        // 摊主确认下架
        post(vendor6, "/vendor/samples/" + sampleId + "/sales-control", java.util.Map.of(
                "sellingStatus", "REMOVED", "salesList", java.util.List.of(), "note", "同批次食品已全部下架"));
        assertEquals(0, get(vendor6, "/vendor/batch-controls").size(), "响应后不再有待处理项");

        // 登记送样
        post(admin, "/admin/lab-tests/" + labId + "/send", java.util.Map.of(
                "labName", "县检验检测中心", "testItems", "菌落总数,大肠菌群,沙门氏菌",
                "receivedDate", "2026-10-06"));

        // 录入不合格：扣保证金200、信用-20、暂停售卖30天、冻结抽签120天
        JsonNode result = post(admin, "/admin/lab-tests/" + labId + "/result", java.util.Map.of(
                "result", "UNQUALIFIED", "resultSummary", "菌落总数、大肠菌群超标，检出沙门氏菌",
                "reportPhotos", "/files/report1.jpg",
                "depositPenalty", 200, "creditDelta", -20, "suspendDays", 30, "lotteryBanDays", 120));
        assertEquals("UNQUALIFIED", result.get("status").asText());

        // 保证金已扣罚、信用已扣、投诉已办结
        JsonNode pays = get(vendor6, "/vendor/payments");
        boolean depositDeducted = false;
        for (JsonNode p : pays) {
            if ("DEPOSIT".equals(p.get("itemType").asText()) && "DEDUCTED".equals(p.get("status").asText())) {
                depositDeducted = true;
            }
        }
        assertTrue(depositDeducted, "保证金应被标记为已扣罚");
        JsonNode creditLogs = get(vendor6, "/vendor/credit-logs");
        assertEquals(-20, creditLogs.get(0).get("delta").asInt());

        // 调整记录含四类联动，且均有原因与来源
        JsonNode adjustments = get(vendor6, "/vendor/adjustments");
        int types = 0;
        for (JsonNode a : adjustments) {
            String t = a.get("type").asText();
            if (t.equals("DEPOSIT_PENALTY") || t.equals("CREDIT_ADJUST")
                    || t.equals("SALES_SUSPEND") || t.equals("LOTTERY_BAN")) {
                types++;
                assertFalse(a.get("reason").asText().isBlank());
                assertEquals("LAB_TEST", a.get("source").asText());
            }
        }
        assertTrue(types >= 4, "应至少生成 4 条联动调整记录，实际: " + types);

        // 暂停售卖期内（30天）报名近期集市熟食 → 拒绝
        long blockedEvent = createOpenEvent(admin, "测试集-暂停期", today.plusDays(15));
        JsonNode err = postExpectFail(vendor6, "/vendor/applications", java.util.Map.of(
                "eventId", blockedEvent, "category", "COLD_DISH",
                "sellsCookedFood", true, "needsPower", true));
        assertTrue(err.get("message").asText().contains("熟食售卖被暂停"));

        // 冻结期内（120天，已过30天暂停期）：可报名，抽签时被标记 BLOCKED
        long decEvent = createOpenEvent(admin, "测试集-冻结期", today.plusDays(60));
        long v6App = applyAndApprove(vendor6, admin, decEvent, "COLD_DISH", true, true);
        // 果蔬摊主同期报名，抽签可正常执行
        applyAndApprove(vendor4, admin, decEvent, "VEG_FRUIT", false, false);
        changeStatus(admin, decEvent, "CLOSED");
        JsonNode decRun = post(admin, "/admin/events/" + decEvent + "/lottery",
                java.util.Map.of("seed", 20261220L));
        assertTrue(decRun.get("remark").asText().contains("资格拦截 1"), decRun.get("remark").asText());

        // 抽签复核仍一致（拦截发生在算法之前，不影响可复核性）
        JsonNode verify = get(admin, "/admin/runs/" + decRun.get("id").asLong() + "/verify");
        assertTrue(verify.get("match").asBoolean(), "复核应一致");

        // v6 报名为 BLOCKED 且有原因
        JsonNode decApps = get(admin, "/admin/events/" + decEvent + "/applications");
        boolean blocked = false;
        for (JsonNode a : decApps) {
            if (a.get("id").asLong() == v6App) {
                blocked = "BLOCKED".equals(a.get("status").asText());
                assertTrue(a.get("blockReason").asText().contains("冻结"), a.get("blockReason").asText());
            }
        }
        assertTrue(blocked, "凉菜摊主应被标记为资格受限(BLOCKED)");
        // 果蔬摊主正常中签
        assertDoesNotThrow(() -> stallNoOfVendor(vendor4, decEvent));

        // 冻结到期后（120天以后）恢复熟食抽签资格
        long junEvent = createOpenEvent(admin, "测试集-冻结到期后", today.plusDays(150));
        applyAndApprove(vendor6, admin, junEvent, "COLD_DISH", true, true);
        changeStatus(admin, junEvent, "CLOSED");
        post(admin, "/admin/events/" + junEvent + "/lottery", java.util.Map.of("seed", 20270601L));
        assertDoesNotThrow(() -> stallNoOfVendor(vendor6, junEvent), "冻结到期后应可正常中签");
    }

    // ================= 场景二：合格结果解除管控 =================

    @Test
    @Order(2)
    void qualifiedFlow_releasesControls() {
        LocalDate today = LocalDate.now();
        String admin = login("admin", "admin123");
        String vendor2 = login("vendor2", "vendor123"); // 李大嫂 糕点

        long eventId = createOpenEvent(admin, "测试集-糕点场", today.plusDays(6));
        applyAndApprove(vendor2, admin, eventId, "PASTRY", false, false);
        changeStatus(admin, eventId, "CLOSED");
        post(admin, "/admin/events/" + eventId + "/lottery", java.util.Map.of("seed", 77L));
        payAll(vendor2);
        String stallNo = stallNoOfVendor(vendor2, eventId);

        JsonNode sample = post(vendor2, "/vendor/samples", java.util.Map.of(
                "eventId", eventId, "productName", "绿豆糕", "batchNo", "P-02",
                "photo", "/files/demo2.jpg", "storageLocation", "2号冷藏柜"));

        long complaintId = publicComplaint(java.util.Map.of(
                "eventId", eventId, "stallNo", stallNo, "reporterName", "王五",
                "type", "QUALITY", "description", "怀疑糕点变质", "foodName", "绿豆糕"));

        JsonNode lab = post(admin, "/admin/lab-tests", java.util.Map.of(
                "complaintId", complaintId, "sampleId", sample.get("id").asLong(),
                "productName", "绿豆糕", "batchNo", "P-02", "batchControl", "KEEP_LIST",
                "suspendNow", true, "suspendDays", 5, "controlNotice", "检测期间暂停糕点售卖"));
        long labId = lab.get("id").asLong();

        // 留存销售名单模式
        post(vendor2, "/vendor/samples/" + sample.get("id").asLong() + "/sales-control", java.util.Map.of(
                "sellingStatus", "KEPT_LIST",
                "salesList", java.util.List.of(
                        java.util.Map.of("buyer", "赵六", "phone", "13800000011", "qty", "1盒",
                                "soldAt", "2026-11-16 09:00", "note", "")),
                "note", "共售 5 盒，名单持续登记"));
        post(admin, "/admin/lab-tests/" + labId + "/send", java.util.Map.of());
        JsonNode result = post(admin, "/admin/lab-tests/" + labId + "/result", java.util.Map.of(
                "result", "QUALIFIED", "resultSummary", "所检项目均符合标准"));
        assertEquals("QUALIFIED", result.get("status").asText());

        // 调查期间临时暂停已解除
        JsonNode suspensions = get(vendor2, "/vendor/suspensions");
        boolean activeFound = false;
        for (JsonNode s : suspensions) {
            if ("ACTIVE".equals(s.get("status").asText())) activeFound = true;
        }
        assertFalse(activeFound, "检测合格后调查期间的暂停措施应已解除");

        // 留样恢复在售
        JsonNode samples = get(vendor2, "/vendor/samples");
        assertEquals("ON_SALE", samples.get(0).get("sellingStatus").asText());

        // 销售名单可在送检单详情看到
        JsonNode detail = get(admin, "/admin/lab-tests/" + labId);
        JsonNode caseSamples = detail.get("case").get("samples");
        boolean listSeen = false;
        for (JsonNode s : caseSamples) {
            if (s.has("salesList") && !s.get("salesList").isNull() && s.get("salesList").asText().contains("赵六")) {
                listSeen = true;
            }
        }
        assertTrue(listSeen, "管理端应能看到摊主登记的销售名单");
    }
}
