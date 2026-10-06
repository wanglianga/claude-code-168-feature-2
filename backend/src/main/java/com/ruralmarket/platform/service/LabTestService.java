package com.ruralmarket.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruralmarket.platform.config.BizException;
import com.ruralmarket.platform.entity.*;
import com.ruralmarket.platform.repository.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 食品投诉关联与留样送检服务。
 *
 * 案件关联：投诉 → 摊主留样 / 购买时间 / 支付凭证 / 同批次消费者 / 巡查记录 / 入场核验。
 * 送检流程：发起送检 → 抽样送机构 → 结果录入；
 * 送检期间对同批次食品要求下架或留存销售名单；
 * 结果不合格联动保证金扣罚、信用扣分、暂停熟食售卖、冻结后续熟食摊位抽签资格，
 * 全部生成 Adjustment 调整记录（原因+来源）并写审计日志，摊主端可见。
 */
@Service
public class LabTestService {
    private final LabTestRepository labTestRepository;
    private final SalesSuspensionRepository suspensionRepository;
    private final ComplaintRepository complaintRepository;
    private final FoodSampleRepository sampleRepository;
    private final PatrolRecordRepository patrolRepository;
    private final EntryCheckRepository entryCheckRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final MarketEventRepository eventRepository;
    private final AdjustmentRepository adjustmentRepository;
    private final AdjustmentService adjustmentService;
    private final AuditService auditService;
    private final ObjectMapper mapper = new ObjectMapper();

    public LabTestService(LabTestRepository labTestRepository, SalesSuspensionRepository suspensionRepository,
                          ComplaintRepository complaintRepository, FoodSampleRepository sampleRepository,
                          PatrolRecordRepository patrolRepository, EntryCheckRepository entryCheckRepository,
                          PaymentRepository paymentRepository, UserRepository userRepository,
                          MarketEventRepository eventRepository,
                          AdjustmentRepository adjustmentRepository, AdjustmentService adjustmentService,
                          AuditService auditService) {
        this.labTestRepository = labTestRepository;
        this.suspensionRepository = suspensionRepository;
        this.complaintRepository = complaintRepository;
        this.sampleRepository = sampleRepository;
        this.patrolRepository = patrolRepository;
        this.entryCheckRepository = entryCheckRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.adjustmentService = adjustmentService;
        this.auditService = auditService;
    }

    // ================= 案件聚合 =================

    /** 投诉案件关联信息：留样、巡查、入场核验、支付凭证、同批次消费者、既有送检单 */
    @Transactional(readOnly = true)
    public Map<String, Object> buildCase(Long complaintId) {
        Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new BizException("投诉不存在"));
        Long eventId = c.getEvent().getId();
        Long vendorId = c.getVendor() != null ? c.getVendor().getId() : null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("complaint", c);
        out.put("vendor", c.getVendor());
        out.put("result", c.getResult());

        // 摊主本集市留样（按留样时间倒序），候选送检
        List<FoodSample> samples = vendorId == null ? List.of()
                : sampleRepository.findByEventIdAndVendorId(eventId, vendorId).stream()
                    .sorted((a, b) -> b.getRetainedAt().compareTo(a.getRetainedAt())).toList();
        out.put("samples", samples);

        // 被投诉摊位的巡查与入场核验
        List<PatrolRecord> patrols = new ArrayList<>();
        EntryCheck entryCheck = null;
        if (c.getResult() != null) {
            patrols = patrolRepository.findByResultId(c.getResult().getId()).stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())).toList();
            entryCheck = entryCheckRepository.findByResultId(c.getResult().getId()).orElse(null);
        }
        out.put("patrols", patrols);
        out.put("entryCheck", entryCheck);

        // 摊主本集市缴费记录（保证金缴纳情况，用于扣罚）
        List<Payment> payments = vendorId == null ? List.of()
                : paymentRepository.findByEventId(eventId).stream()
                    .filter(p -> p.getVendor().getId().equals(vendorId)).toList();
        out.put("payments", payments);

        // 同批次消费者：同集市同摊主的其他消费者投诉，按批次号/食品名/购买时间判定
        List<Map<String, Object>> related = new ArrayList<>();
        if (vendorId != null) {
            Set<String> batchNos = new HashSet<>();
            samples.forEach(s -> batchNos.add(s.getBatchNo()));
            if (c.getBatchNo() != null && !c.getBatchNo().isBlank()) batchNos.add(c.getBatchNo());
            for (Complaint other : complaintRepository.findByEventIdOrderByCreatedAtDesc(eventId)) {
                if (other.getId().equals(c.getId()) || !"CONSUMER".equals(other.getReporterType())) continue;
                if (other.getVendor() == null || !other.getVendor().getId().equals(vendorId)) continue;
                List<String> reasons = matchReasons(c, other, batchNos);
                if (!reasons.isEmpty()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("complaint", other);
                    m.put("matchReasons", reasons);
                    related.add(m);
                }
            }
        }
        out.put("relatedConsumers", related);
        out.put("labTests", labTestRepository.findByComplaintId(complaintId));
        return out;
    }

    private List<String> matchReasons(Complaint current, Complaint other, Set<String> batchNos) {
        List<String> reasons = new ArrayList<>();
        if (other.getBatchNo() != null && !other.getBatchNo().isBlank()
                && batchNos.contains(other.getBatchNo())) {
            reasons.add("同批次(" + other.getBatchNo() + ")");
        }
        if (current.getFoodName() != null && current.getFoodName().equalsIgnoreCase(other.getFoodName())) {
            reasons.add("同食品(" + current.getFoodName() + ")");
        }
        // 购买时间间隔 24 小时内的腹泻投诉，视为时间邻近
        if (current.getPurchaseTime() != null && other.getPurchaseTime() != null
                && Math.abs(java.time.Duration.between(current.getPurchaseTime(), other.getPurchaseTime()).toHours()) <= 24) {
            reasons.add("购买时间邻近");
        }
        return reasons;
    }

    // ================= 发起送检 =================

    public record CreateReq(@NotNull Long complaintId, Long sampleId, @NotBlank String productName,
                            @NotBlank String batchNo, String labName, String testItems,
                            @NotBlank String batchControl, String controlNotice,
                            Boolean suspendNow, Integer suspendDays, String suspendReason) {}

    @Transactional
    public LabTest create(CreateReq req, User operator) {
        Complaint c = complaintRepository.findById(req.complaintId())
                .orElseThrow(() -> new BizException("投诉不存在"));
        if (c.getVendor() == null) throw new BizException("该投诉未关联摊主，无法送检");
        if (!"REMOVE".equals(req.batchControl()) && !"KEEP_LIST".equals(req.batchControl())) {
            throw new BizException("同批次管控方式必须为 REMOVE(下架) 或 KEEP_LIST(留存销售名单)");
        }
        FoodSample sample = null;
        if (req.sampleId() != null) {
            sample = sampleRepository.findById(req.sampleId())
                    .orElseThrow(() -> new BizException("留样不存在"));
            if (!sample.getVendor().getId().equals(c.getVendor().getId())) {
                throw new BizException("留样摊主与投诉涉及摊主不一致");
            }
            if (!sample.getEvent().getId().equals(c.getEvent().getId())) {
                throw new BizException("留样与投诉不属于同一场集市");
            }
        }

        LabTest t = new LabTest();
        t.setEvent(c.getEvent());
        t.setVendor(c.getVendor());
        t.setComplaint(c);
        t.setSample(sample);
        t.setProductName(req.productName());
        t.setBatchNo(req.batchNo());
        t.setLabName(req.labName());
        t.setTestItems(req.testItems());
        t.setBatchControl(req.batchControl());
        t.setControlNotice(req.controlNotice());
        t.setStatus("INITIATED");
        t = labTestRepository.save(t);

        // 标记留样进入送检流程，并下发同批次管控要求
        if (sample != null) {
            sample.setStatus("LAB_TESTING");
            sample.setRemark(append(sample.getRemark(),
                    "送检单#" + t.getId() + " 已发起，同批次管控："
                            + ("REMOVE".equals(req.batchControl()) ? "立即下架" : "留存销售名单")));
            sampleRepository.save(sample);
        }
        // 投诉进入处理中
        if ("SUBMITTED".equals(c.getStatus())) {
            c.setStatus("PROCESSING");
            c.setHandler(operator);
            c.setHandledAt(LocalDateTime.now());
            complaintRepository.save(c);
        }

        // 调查期间可同步暂停该摊主熟食售卖
        if (Boolean.TRUE.equals(req.suspendNow())) {
            int days = req.suspendDays() == null ? 3 : Math.max(1, req.suspendDays());
            String reason = (req.suspendReason() == null || req.suspendReason().isBlank())
                    ? "投诉#" + c.getId() + " 引发食品安全调查，送检期间暂停熟食售卖" : req.suspendReason();
            createSuspension(c.getVendor(), c.getEvent(), t, "SALES_SUSPEND",
                    LocalDate.now(), LocalDate.now().plusDays(days), reason,
                    "INVESTIGATING", t.getId(), operator, false);
        }

        auditService.log(operator.getId(), operator.getName(), "LAB_TEST_CREATE",
                "发起留样送检#" + t.getId() + "：摊主[" + c.getVendor().getName() + "] "
                        + req.productName() + " 批次" + req.batchNo()
                        + "，同批次管控=" + ("REMOVE".equals(req.batchControl()) ? "下架" : "留存名单")
                        + "，来源投诉#" + c.getId());
        return t;
    }

    // ================= 抽样送机构 =================

    public record SendReq(String labName, String testItems, String receivedDate, String remark) {}

    @Transactional
    public LabTest send(Long id, SendReq req, User operator) {
        LabTest t = mustGet(id);
        if ("QUALIFIED".equals(t.getStatus()) || "UNQUALIFIED".equals(t.getStatus())) {
            throw new BizException("送检单已有检测结果");
        }
        if ("CANCELLED".equals(t.getStatus())) throw new BizException("送检单已撤销");
        t.setStatus("SENT");
        t.setSampledBy(operator);
        t.setSampledAt(LocalDateTime.now());
        if (req.labName() != null && !req.labName().isBlank()) t.setLabName(req.labName());
        if (req.testItems() != null && !req.testItems().isBlank()) t.setTestItems(req.testItems());
        if (req.receivedDate() != null && !req.receivedDate().isBlank()) {
            t.setReceivedDate(LocalDate.parse(req.receivedDate()));
        }
        LabTest saved = labTestRepository.save(t);
        if (t.getSample() != null) {
            FoodSample s = t.getSample();
            s.setStatus("LAB_SENT");
            if (req.remark() != null && !req.remark().isBlank()) {
                s.setRemark(append(s.getRemark(), "送样备注：" + req.remark()));
            }
            sampleRepository.save(s);
        }
        auditService.log(operator.getId(), operator.getName(), "LAB_TEST_SEND",
                "送检单#" + id + " 抽样并送机构[" + t.getLabName() + "]，项目：" + t.getTestItems());
        return saved;
    }

    // ================= 录入检测结果 =================

    public record ResultReq(@NotBlank String result, @NotBlank String resultSummary, String reportPhotos,
                            Integer depositPenalty, Integer creditDelta,
                            Integer suspendDays, Integer lotteryBanDays) {}

    @Transactional
    public LabTest reportResult(Long id, ResultReq req, User operator) {
        LabTest t = mustGet(id);
        if ("QUALIFIED".equals(t.getStatus()) || "UNQUALIFIED".equals(t.getStatus())) {
            throw new BizException("检测结果已录入，不可重复录入");
        }
        if ("CANCELLED".equals(t.getStatus())) throw new BizException("送检单已撤销");
        boolean qualified = "QUALIFIED".equals(req.result());
        if (!qualified && !"UNQUALIFIED".equals(req.result())) {
            throw new BizException("检测结果必须为 QUALIFIED 或 UNQUALIFIED");
        }

        t.setStatus(qualified ? "QUALIFIED" : "UNQUALIFIED");
        t.setResultSummary(req.resultSummary());
        t.setReportPhotos(req.reportPhotos());
        t.setReporter(operator);
        t.setReportedAt(LocalDateTime.now());

        if (qualified) {
            // 合格：解除调查期间临时管控与同批次下架要求
            t.setDepositPenalty(0);
            t.setCreditDelta(0);
            t.setSuspendDays(0);
            t.setLotteryBanDays(0);
            labTestRepository.save(t);
            releaseInvestigatingControls(t, operator);
            auditService.log(operator.getId(), operator.getName(), "LAB_RESULT",
                    "送检单#" + id + " 检测合格：" + req.resultSummary() + "，已解除调查管控");
            return t;
        }

        // ===== 不合格：保证金 / 信用 / 暂停售卖 / 抽签资格 联动处理 =====
        int penalty = Math.max(0, req.depositPenalty() == null ? 0 : req.depositPenalty());
        int creditDelta = req.creditDelta() == null || req.creditDelta() > 0 ? 0 : req.creditDelta();
        int suspendDays = Math.max(0, req.suspendDays() == null ? 0 : req.suspendDays());
        int banDays = Math.max(0, req.lotteryBanDays() == null ? 0 : req.lotteryBanDays());
        t.setDepositPenalty(penalty);
        t.setCreditDelta(creditDelta);
        t.setSuspendDays(suspendDays);
        t.setLotteryBanDays(banDays);
        labTestRepository.save(t);

        String baseReason = "送检单#" + t.getId() + " 批次" + t.getBatchNo() + " 检测不合格："
                + truncate(req.resultSummary());

        // 1) 保证金扣罚（复用调整服务，生成 DEPOSIT_PENALTY 调整记录）
        if (penalty > 0) {
            Payment deposit = paymentRepository.findByEventId(t.getEvent().getId()).stream()
                    .filter(p -> p.getVendor().getId().equals(t.getVendor().getId())
                            && "DEPOSIT".equals(p.getItemType()) && !"DEDUCTED".equals(p.getStatus()))
                    .findFirst().orElse(null);
            if (deposit != null) {
                String reason = baseReason + "；扣罚保证金 ¥" + penalty
                        + (penalty < deposit.getAmount().doubleValue()
                            ? "（保证金 ¥" + deposit.getAmount() + "，余额按线下流程退还/追缴）" : "");
                adjustmentService.penalizeDeposit(deposit.getId(), reason, "LAB_TEST", t.getId(), operator);
            } else {
                // 无保证金账单：生成一条行政扣罚单（线下追缴）并留痕
                Payment p = new Payment();
                p.setEvent(t.getEvent());
                if (t.getComplaint().getResult() != null) p.setResult(t.getComplaint().getResult());
                p.setVendor(t.getVendor());
                p.setItemType("PENALTY");
                p.setItemName("食品安全扣罚款（送检#" + t.getId() + "）");
                p.setAmount(new java.math.BigDecimal(penalty));
                p.setStatus("DEDUCTED");
                p.setPaidAt(LocalDateTime.now());
                p.setRemark(baseReason);
                paymentRepository.save(p);
                Adjustment adj = adjustment(t, "DEPOSIT_PENALTY", "保证金账户无在缴保证金",
                        "行政扣罚 ¥" + penalty + "（线下追缴）", baseReason, operator);
                adjustmentRepository.save(adj);
            }
        }

        // 2) 信用扣分
        if (creditDelta < 0) {
            adjustmentService.adjustCredit(t.getVendor().getId(), creditDelta,
                    baseReason + "；信用 " + creditDelta + " 分", "LAB_TEST", t.getId(), operator);
        }

        // 3) 暂停熟食售卖
        if (suspendDays > 0) {
            LocalDate start = LocalDate.now();
            createSuspension(t.getVendor(), t.getEvent(), t, "SALES_SUSPEND", start,
                    start.plusDays(suspendDays),
                    baseReason + "；暂停熟食售卖 " + suspendDays + " 天",
                    "LAB_UNQUALIFIED", t.getId(), operator, true);
        }

        // 4) 后续熟食摊位抽签资格冻结
        if (banDays > 0) {
            LocalDate start = LocalDate.now();
            createSuspension(t.getVendor(), t.getEvent(), t, "LOTTERY_BAN", start,
                    start.plusDays(banDays),
                    baseReason + "；冻结熟食摊位抽签资格 " + banDays + " 天",
                    "LAB_UNQUALIFIED", t.getId(), operator, true);
        }

        // 5) 留样标记异常，调查期间临时管控被正式处罚替代
        if (t.getSample() != null) {
            FoodSample s = t.getSample();
            s.setStatus("ABNORMAL");
            s.setRemark(append(s.getRemark(), "送检#" + t.getId() + " 不合格，留样标记异常并按规定处置"));
            sampleRepository.save(s);
        }
        replaceInvestigatingControls(t, operator);

        // 6) 投诉可办结
        Complaint c = t.getComplaint();
        c.setStatus("RESOLVED");
        c.setResolution(truncate("检测不合格处理：" + req.resultSummary()
                + (penalty > 0 ? "；扣保证金¥" + penalty : "")
                + (creditDelta < 0 ? "；信用" + creditDelta : "")
                + (suspendDays > 0 ? "；暂停熟食售卖" + suspendDays + "天" : "")
                + (banDays > 0 ? "；冻结熟食抽签资格" + banDays + "天" : ""), 1900));
        c.setHandler(operator);
        c.setHandledAt(LocalDateTime.now());
        complaintRepository.save(c);

        auditService.log(operator.getId(), operator.getName(), "LAB_RESULT",
                "送检单#" + id + " 检测不合格，联动：保证金扣罚¥" + penalty
                        + "、信用" + creditDelta + "、暂停售卖" + suspendDays + "天"
                        + "、抽签冻结" + banDays + "天");
        return t;
    }

    /** 合格后解除：调查期间临时暂停 + 同批次下架要求 */
    private void releaseInvestigatingControls(LabTest t, User operator) {
        suspensionRepository.findByVendorIdOrderByCreatedAtDesc(t.getVendor().getId()).stream()
                .filter(s -> s.getLabTest() != null && s.getLabTest().getId().equals(t.getId())
                        && "ACTIVE".equals(s.getStatus()) && "INVESTIGATING".equals(s.getReasonType()))
                .forEach(s -> revoke(s, "检测合格，自动解除调查管控", operator));
        if (t.getSample() != null) {
            FoodSample s = t.getSample();
            s.setStatus("KEPT");
            s.setSellingStatus("ON_SALE");
            s.setRemark(append(s.getRemark(), "送检#" + t.getId() + " 合格，恢复正常售卖"));
            sampleRepository.save(s);
        }
    }

    /** 不合格后：调查期间临时措施到期终止，以正式处罚为准 */
    private void replaceInvestigatingControls(LabTest t, User operator) {
        suspensionRepository.findByVendorIdOrderByCreatedAtDesc(t.getVendor().getId()).stream()
                .filter(s -> s.getLabTest() != null && s.getLabTest().getId().equals(t.getId())
                        && "ACTIVE".equals(s.getStatus()) && "INVESTIGATING".equals(s.getReasonType()))
                .forEach(s -> {
                    s.setStatus("EXPIRED");
                    s.setRevokeReason("检测不合格，转为正式处罚");
                    s.setRevokedBy(operator);
                    s.setRevokedAt(LocalDateTime.now());
                    suspensionRepository.save(s);
                });
    }

    // ================= 暂停/冻结 管理 =================

    public record SuspendReq(@NotNull Long vendorId, Long eventId, @NotBlank String type,
                             @NotBlank String reason, @NotBlank String reasonType,
                             Integer days, Long labTestId) {}

    @Transactional
    public SalesSuspension suspend(SuspendReq req, User operator) {
        User vendor = userRepository.findById(req.vendorId())
                .orElseThrow(() -> new BizException("摊主不存在"));
        if (!Set.of("SALES_SUSPEND", "LOTTERY_BAN", "BOTH").contains(req.type())) {
            throw new BizException("非法暂停类型");
        }
        MarketEvent ev = null;
        if (req.eventId() != null) {
            ev = eventRepository.findById(req.eventId()).orElseThrow(() -> new BizException("集市不存在"));
        }
        // 送检单可推导集市
        LabTest lt = null;
        if (req.labTestId() != null) {
            lt = labTestRepository.findById(req.labTestId()).orElse(null);
            if (ev == null && lt != null) ev = lt.getEvent();
        }
        int days = req.days() == null ? 0 : Math.max(0, req.days());
        LocalDate start = LocalDate.now();
        LocalDate end = days > 0 ? start.plusDays(days) : null;
        String reasonType = Set.of("INVESTIGATING", "LAB_UNQUALIFIED", "MANUAL").contains(req.reasonType())
                ? req.reasonType() : "MANUAL";
        SalesSuspension saved = createSuspension(vendor, ev, lt, req.type(), start, end,
                req.reason(), reasonType, req.labTestId(), operator, true);
        auditService.log(operator.getId(), operator.getName(), "SALES_SUSPEND",
                "摊主[" + vendor.getName() + "] " + typeText(req.type())
                        + (days > 0 ? " " + days + " 天" : "（长期）") + "，原因：" + req.reason());
        return saved;
    }

    @Transactional
    public SalesSuspension revokeSuspension(Long suspensionId, String reason, User operator) {
        SalesSuspension s = suspensionRepository.findById(suspensionId)
                .orElseThrow(() -> new BizException("暂停记录不存在"));
        if (!"ACTIVE".equals(s.getStatus())) throw new BizException("该记录已不在生效中");
        revoke(s, reason, operator);
        auditService.log(operator.getId(), operator.getName(), "SUSPENSION_REVOKE",
                "解除摊主[" + s.getVendor().getName() + "] " + typeText(s.getType()) + "，原因：" + reason);
        return s;
    }

    private void revoke(SalesSuspension s, String reason, User operator) {
        s.setStatus("REVOKED");
        s.setRevokedBy(operator);
        s.setRevokeReason(reason);
        s.setRevokedAt(LocalDateTime.now());
        suspensionRepository.save(s);
    }

    // ================= 资格判断（抽签联动） =================

    /** 摊主在指定赶集日是否被冻结熟食摊位抽签资格 */
    @Transactional(readOnly = true)
    public Optional<SalesSuspension> cookedLotteryBan(User vendor, LocalDate marketDate) {
        LocalDate day = marketDate == null ? LocalDate.now() : marketDate;
        return suspensionRepository.findByVendorIdAndStatusOrderByCreatedAtDesc(vendor.getId(), "ACTIVE").stream()
                .filter(s -> ("LOTTERY_BAN".equals(s.getType()) || "BOTH".equals(s.getType())))
                .filter(s -> s.getEndDate() == null || !s.getEndDate().isBefore(day))
                .findFirst();
    }

    /** 摊主当前是否被暂停熟食售卖 */
    @Transactional(readOnly = true)
    public Optional<SalesSuspension> cookedSalesSuspended(User vendor, LocalDate day0) {
        LocalDate day = day0 == null ? LocalDate.now() : day0;
        return suspensionRepository.findByVendorIdAndStatusOrderByCreatedAtDesc(vendor.getId(), "ACTIVE").stream()
                .filter(s -> ("SALES_SUSPEND".equals(s.getType()) || "BOTH".equals(s.getType())))
                .filter(s -> s.getEndDate() == null || !s.getEndDate().isBefore(day))
                .findFirst();
    }

    /** 摊主管控状态总览（摊主端/管理端用） */
    @Transactional(readOnly = true)
    public Map<String, Object> eligibility(Long vendorId, String date) {
        User vendor = userRepository.findById(vendorId).orElseThrow(() -> new BizException("摊主不存在"));
        LocalDate day = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("vendor", vendor);
        out.put("date", day);
        out.put("salesSuspended", cookedSalesSuspended(vendor, day).orElse(null));
        out.put("lotteryBanned", cookedLotteryBan(vendor, day).orElse(null));
        out.put("activeSuspensions", suspensionRepository
                .findByVendorIdAndStatusOrderByCreatedAtDesc(vendorId, "ACTIVE"));
        return out;
    }

    // ================= 摊主端：同批次管控响应 =================

    @Transactional
    public FoodSample submitSalesControl(Long sampleId, String sellingStatus, List<Map<String, Object>> salesList,
                                         String note, User vendor) {
        FoodSample s = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new BizException("留样不存在"));
        if (!s.getVendor().getId().equals(vendor.getId())) {
            throw new BizException("无权操作他人留样");
        }
        if (!Set.of("REMOVED", "KEPT_LIST", "ON_SALE", "SOLD_OUT").contains(sellingStatus)) {
            throw new BizException("非法在售状态");
        }
        s.setSellingStatus(sellingStatus);
        s.setSalesListNote(note);
        if (salesList != null) {
            try {
                s.setSalesList(mapper.writeValueAsString(salesList));
            } catch (Exception e) {
                throw new BizException("销售名单格式错误");
            }
        }
        s.setSalesListUpdatedAt(LocalDateTime.now());
        FoodSample saved = sampleRepository.save(s);
        auditService.log(vendor.getId(), vendor.getName(), "BATCH_CONTROL_FEEDBACK",
                "留样#" + sampleId + " 批次" + s.getBatchNo() + " 管控响应："
                        + sellingStatusText(sellingStatus)
                        + (salesList != null ? "，名单" + salesList.size() + " 人" : "")
                        + (note != null ? "，说明：" + note : ""));
        return saved;
    }

    // ================= 内部辅助 =================

    private SalesSuspension createSuspension(User vendor, MarketEvent event, LabTest lt, String type,
                                             LocalDate start, LocalDate end, String reason,
                                             String reasonType, Long sourceRefId,
                                             User operator, boolean withAdjustment) {
        SalesSuspension s = new SalesSuspension();
        s.setVendor(vendor);
        s.setEvent(event);
        s.setLabTest(lt);
        s.setType(type);
        s.setStartDate(start);
        s.setEndDate(end);
        s.setReason(reason);
        s.setReasonType(reasonType);
        s.setSourceRefId(sourceRefId);
        s.setOperator(operator);
        SalesSuspension saved = suspensionRepository.save(s);

        if (withAdjustment) {
            String after = typeText(type) + "：" + start + " 至 " + (end == null ? "解除日" : end);
            Adjustment adj = lt != null
                    ? adjustment(lt, type, "无", after, reason, operator)
                    : plainAdjustment(vendor, event, type, after, reason, sourceRefId, operator);
            adjustmentRepository.save(adj);
        }
        return saved;
    }

    private Adjustment adjustment(LabTest t, String type, String before, String after,
                                  String reason, User operator) {
        Adjustment adj = new Adjustment();
        adj.setEvent(t.getEvent());
        if (t.getComplaint().getResult() != null) adj.setResult(t.getComplaint().getResult());
        adj.setVendor(t.getVendor());
        adj.setType(type);
        adj.setBeforeValue(before);
        adj.setAfterValue(after);
        adj.setReason(reason);
        adj.setSource("LAB_TEST");
        adj.setSourceRefId(t.getId());
        adj.setOperator(operator);
        return adj;
    }

    private Adjustment plainAdjustment(User vendor, MarketEvent event, String type, String after,
                                       String reason, Long sourceRefId, User operator) {
        Adjustment adj = new Adjustment();
        adj.setEvent(event);
        adj.setVendor(vendor);
        adj.setType(type);
        adj.setAfterValue(after);
        adj.setReason(reason);
        adj.setSource("LAB_TEST");
        adj.setSourceRefId(sourceRefId);
        adj.setOperator(operator);
        return adj;
    }

    private LabTest mustGet(Long id) {
        return labTestRepository.findById(id).orElseThrow(() -> new BizException("送检单不存在"));
    }

    private String append(String origin, String add) {
        if (origin == null || origin.isBlank()) return add;
        return origin + "；" + add;
    }

    private String truncate(String s) {
        return truncate(s, 900);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    public static String typeText(String type) {
        return switch (type) {
            case "SALES_SUSPEND" -> "暂停熟食售卖";
            case "LOTTERY_BAN" -> "冻结熟食摊位抽签资格";
            case "BOTH" -> "暂停熟食售卖并冻结抽签资格";
            default -> type;
        };
    }

    public static String sellingStatusText(String s) {
        return switch (s) {
            case "REMOVED" -> "已下架";
            case "KEPT_LIST" -> "留存销售名单";
            case "SOLD_OUT" -> "已售罄";
            default -> "在售";
        };
    }
}
