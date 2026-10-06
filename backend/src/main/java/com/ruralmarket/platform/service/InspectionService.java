package com.ruralmarket.platform.service;

import com.ruralmarket.platform.config.BizException;
import com.ruralmarket.platform.entity.*;
import com.ruralmarket.platform.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 食品投诉溯源与留样送检服务。
 *
 * 食品安全投诉（腹泻等）受理后，平台聚合：摊主留样、消费者购买时间、支付凭证、
 * 同批次其他消费者投诉、巡查记录、保证金情况，辅助管理员判断。
 * 管理员可发起留样送检：送检期间暂停该摊主熟食售卖，并要求同批次食品下架或留存销售名单；
 * 检测结果登记后联动保证金扣罚、信用扣分、后续熟食摊位抽签资格限制，全程留痕可追溯。
 */
@Service
public class InspectionService {
    /** 留样法定保存时长（小时），用于按购买时间圈定同批次留样/消费者 */
    public static final int RETAIN_HOURS = 48;
    /** 送检未决状态 */
    public static final List<String> PENDING_STATUSES = List.of("PENDING", "IN_LAB");

    private final FoodInspectionRepository inspectionRepository;
    private final VendorRestrictionRepository restrictionRepository;
    private final ComplaintRepository complaintRepository;
    private final FoodSampleRepository sampleRepository;
    private final PatrolRecordRepository patrolRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AdjustmentService adjustmentService;
    private final CreditService creditService;
    private final AuditService auditService;

    public InspectionService(FoodInspectionRepository inspectionRepository,
                             VendorRestrictionRepository restrictionRepository,
                             ComplaintRepository complaintRepository, FoodSampleRepository sampleRepository,
                             PatrolRecordRepository patrolRepository, PaymentRepository paymentRepository,
                             UserRepository userRepository, AdjustmentService adjustmentService,
                             CreditService creditService, AuditService auditService) {
        this.inspectionRepository = inspectionRepository;
        this.restrictionRepository = restrictionRepository;
        this.complaintRepository = complaintRepository;
        this.sampleRepository = sampleRepository;
        this.patrolRepository = patrolRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.adjustmentService = adjustmentService;
        this.creditService = creditService;
        this.auditService = auditService;
    }

    // ================= 投诉溯源聚合 =================
    @Transactional(readOnly = true)
    public Map<String, Object> trace(Long complaintId) {
        Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new BizException("投诉不存在"));
        User vendor = c.getVendor();
        MarketEvent event = c.getEvent();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("complaint", c);

        if (vendor == null) {
            out.put("vendor", null);
            return out;
        }
        out.put("vendor", vendor);
        out.put("restriction", restrictionRepository.findByVendorId(vendor.getId()).orElse(null));

        // 1) 摊主留样：按购买时间圈定 48 小时内、留样时间不晚于购买时间的同批次可疑留样
        List<FoodSample> samples = sampleRepository.findByEventIdAndVendorId(event.getId(), vendor.getId());
        List<Map<String, Object>> sampleList = new ArrayList<>();
        for (FoodSample s : samples) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sample", s);
            m.put("relevant", isRelevantSample(s, c.getPurchaseTime()));
            sampleList.add(m);
        }
        sampleList.sort((a, b) -> {
            FoodSample x = (FoodSample) a.get("sample");
            FoodSample y = (FoodSample) b.get("sample");
            return y.getRetainedAt().compareTo(x.getRetainedAt());
        });
        out.put("samples", sampleList);

        // 2) 支付凭证（消费者上传）与摊主保证金账单
        out.put("consumerPaymentProof", c.getPaymentProof());
        List<Payment> deposits = paymentRepository.findByEventId(event.getId()).stream()
                .filter(p -> p.getVendor().getId().equals(vendor.getId()) && "DEPOSIT".equals(p.getItemType()))
                .toList();
        out.put("deposits", deposits);

        // 3) 同批次其他消费者：同集市同摊主，购买时间落在任一相关留样 48h 窗口内
        List<Map<String, Object>> relatedConsumers = new ArrayList<>();
        for (Complaint other : complaintRepository.findByEventIdOrderByCreatedAtDesc(event.getId())) {
            if (other.getId().equals(c.getId()) || vendor == null || other.getVendor() == null) continue;
            if (!"CONSUMER".equals(other.getReporterType())) continue;
            if (!other.getVendor().getId().equals(vendor.getId())) continue;
            boolean sameBatch = other.getPurchaseTime() != null && samples.stream()
                    .anyMatch(s -> withinRetainWindow(s, other.getPurchaseTime()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("complaint", other);
            m.put("sameBatch", sameBatch);
            relatedConsumers.add(m);
        }
        out.put("relatedConsumers", relatedConsumers);

        // 4) 巡查记录（该摊主本集市全部摊位）
        List<PatrolRecord> patrols = patrolRepository.findByEventIdOrderByCreatedAtDesc(event.getId()).stream()
                .filter(p -> p.getResult() != null
                        && p.getResult().getApplication() != null
                        && p.getResult().getApplication().getVendor() != null
                        && p.getResult().getApplication().getVendor().getId().equals(vendor.getId()))
                .toList();
        out.put("patrols", patrols);

        // 5) 已有关联送检单
        out.put("inspection", inspectionRepository.findByComplaintId(c.getId()).orElse(null));
        out.put("retainHours", RETAIN_HOURS);
        return out;
    }

    /** 留样时间不晚于购买时间，且购买时间在留样后 48 小时内 → 相关同批次留样 */
    private boolean isRelevantSample(FoodSample s, LocalDateTime purchaseTime) {
        return withinRetainWindow(s, purchaseTime);
    }

    private boolean withinRetainWindow(FoodSample s, LocalDateTime purchaseTime) {
        if (purchaseTime == null) return false;
        LocalDateTime retained = s.getRetainedAt();
        return !purchaseTime.isBefore(retained)
                && !purchaseTime.isAfter(retained.plusHours(RETAIN_HOURS));
    }

    // ================= 发起留样送检 =================
    public record CreateInspectionReq(Long complaintId, Long sampleId,
                                      String labName, String testItems,
                                      String batchAction, Boolean suspendCooked) {}

    @Transactional
    public FoodInspection create(CreateInspectionReq req, User operator) {
        if (req.complaintId() == null || req.sampleId() == null) {
            throw new BizException("缺少投诉或留样参数");
        }
        if (req.labName() == null || req.labName().isBlank()) {
            throw new BizException("请填写承检机构");
        }
        if (req.batchAction() == null) throw new BizException("请选择同批次处置方式");
        Complaint c = complaintRepository.findById(req.complaintId())
                .orElseThrow(() -> new BizException("投诉不存在"));
        if (c.getVendor() == null) throw new BizException("该投诉未关联摊主，无法送检");
        FoodSample sample = sampleRepository.findById(req.sampleId())
                .orElseThrow(() -> new BizException("留样不存在"));
        if (!sample.getVendor().getId().equals(c.getVendor().getId())) {
            throw new BizException("送检留样与被投诉摊主不一致");
        }
        if (!List.of("REMOVE", "KEEP_LIST").contains(req.batchAction())) {
            throw new BizException("同批次处置方式非法");
        }
        inspectionRepository.findByComplaintId(c.getId()).ifPresent(x -> {
            throw new BizException("该投诉已发起送检（送检单 #" + x.getId() + "）");
        });

        FoodInspection in = new FoodInspection();
        in.setEvent(c.getEvent());
        in.setVendor(c.getVendor());
        in.setComplaint(c);
        in.setSample(sample);
        in.setResult(c.getResult());
        in.setBatchNo(sample.getBatchNo());
        in.setLabName(req.labName());
        in.setTestItems(req.testItems());
        in.setBatchAction(req.batchAction());
        boolean suspend = req.suspendCooked() == null || req.suspendCooked();
        in.setCookedSuspended(suspend);
        in.setStatus("IN_LAB");
        in.setSentAt(LocalDateTime.now());
        in.setCreatedBy(operator);
        FoodInspection saved = inspectionRepository.save(in);

        // 送检期间暂停该摊主熟食售卖
        if (suspend) {
            VendorRestriction r = getOrCreate(c.getVendor());
            r.setCookedSalesSuspended(true);
            r.setSuspensionReason("食品安全投诉#" + c.getId() + " 留样送检期间暂停熟食售卖（批次 " + sample.getBatchNo() + "）");
            r.setSuspensionInspectionId(saved.getId());
            r.setSuspensionUntil(null);
            r.setUpdatedBy(operator);
            r.setUpdatedAt(LocalDateTime.now());
            restrictionRepository.save(r);
        }

        // 投诉进入处理中
        c.setStatus("PROCESSING");
        c.setHandler(operator);
        complaintRepository.save(c);

        auditService.log(operator.getId(), operator.getName(), "INSPECTION_CREATE",
                "投诉#" + c.getId() + " 发起留样送检，摊主[" + c.getVendor().getName()
                        + "] 批次[" + sample.getBatchNo() + "] 送[" + req.labName() + "]，"
                        + (suspend ? "暂停熟食售卖，" : "")
                        + ("REMOVE".equals(req.batchAction()) ? "要求同批次下架" : "要求留存同批次销售名单"));
        return saved;
    }

    // ================= 摊主同批次处置回执 =================
    @Transactional
    public FoodInspection receipt(Long inspectionId, String receiptStatus, String receipt, User vendor) {
        FoodInspection in = mustGet(inspectionId);
        if (!in.getVendor().getId().equals(vendor.getId())) {
            throw new BizException("无权处理他人送检单");
        }
        if (!List.of("REMOVED", "LIST_KEPT").contains(receiptStatus)) {
            throw new BizException("回执状态非法");
        }
        in.setBatchReceiptStatus(receiptStatus);
        in.setBatchReceipt(receipt);
        in.setBatchReceiptAt(LocalDateTime.now());
        FoodInspection saved = inspectionRepository.save(in);
        auditService.log(vendor.getId(), vendor.getName(), "INSPECTION_RECEIPT",
                "送检单#" + in.getId() + " 摊主回执：" + ("REMOVED".equals(receiptStatus) ? "同批次已下架" : "已留存销售名单")
                        + (receipt != null && !receipt.isBlank() ? "，说明：" + receipt : ""));
        return saved;
    }

    // ================= 登记检测结果（联动处置） =================
    public record ResultReq(String status, String resultSummary, String reportPhoto,
                            Boolean deductDeposit, Integer creditDelta,
                            Boolean disqualifyCooked, String disqualifyUntil, String handleNote) {}

    @Transactional
    public FoodInspection recordResult(Long inspectionId, ResultReq req, User operator) {
        FoodInspection in = mustGet(inspectionId);
        if (req.status() == null || (!"QUALIFIED".equals(req.status()) && !"UNQUALIFIED".equals(req.status()))) {
            throw new BizException("请选择检测结果（合格/不合格）");
        }
        if (req.resultSummary() == null || req.resultSummary().isBlank()) {
            throw new BizException("请填写检测结果说明");
        }
        if (!PENDING_STATUSES.contains(in.getStatus())) {
            throw new BizException("该送检单已登记结果（当前：" + statusText(in.getStatus()) + "）");
        }
        boolean unqualified = "UNQUALIFIED".equals(req.status());
        boolean qualified = "QUALIFIED".equals(req.status());
        if (!unqualified && !qualified) throw new BizException("检测结果状态非法");

        in.setStatus(req.status());
        in.setResultSummary(req.resultSummary());
        in.setReportPhoto(req.reportPhoto());
        in.setResultBy(operator);
        in.setResultAt(LocalDateTime.now());

        User vendor = in.getVendor();
        VendorRestriction r = getOrCreate(vendor);
        String reasonBase = "送检单#" + in.getId() + "（投诉#" + in.getComplaint().getId()
                + "，批次 " + in.getBatchNo() + "）检测" + (unqualified ? "不合格" : "合格");

        if (unqualified) {
            // 1) 保证金扣罚（复用调整留痕，摊主端可见）
            if (Boolean.TRUE.equals(req.deductDeposit())) {
                Payment deposit = paymentRepository.findByEventId(in.getEvent().getId()).stream()
                        .filter(p -> p.getVendor().getId().equals(vendor.getId())
                                && "DEPOSIT".equals(p.getItemType())
                                && !"DEDUCTED".equals(p.getStatus()))
                        .findFirst().orElse(null);
                if (deposit != null) {
                    adjustmentService.penalizeDeposit(deposit.getId(),
                            reasonBase + "，保证金扣罚", "COMPLAINT", in.getComplaint().getId(), operator);
                    in.setDepositDeducted(deposit.getAmount());
                } else {
                    in.setDepositDeducted(BigDecimal.ZERO);
                }
            }
            // 2) 信用扣分
            int delta = req.creditDelta() == null ? 0 : req.creditDelta();
            if (delta != 0) {
                creditService.adjust(vendor, delta, reasonBase + "，信用扣分",
                        "INSPECTION", in.getId(), operator);
                in.setCreditDelta(delta);
            }
            // 3) 取消后续熟食摊位抽签资格
            if (Boolean.TRUE.equals(req.disqualifyCooked())) {
                LocalDate until = parseDate(req.disqualifyUntil());
                r.setCookedLotteryBanned(true);
                r.setBanReason(reasonBase + "，取消熟食摊位抽签资格" + (until != null ? "至 " + until : "（永久）"));
                r.setBanInspectionId(in.getId());
                r.setBanUntil(until);
                in.setDisqualifyCooked(true);
                in.setDisqualifyUntil(until);
            }
            // 不合格：强制暂停熟食售卖（绑定本送检单，整改后由管理员人工解除）
            if (Boolean.TRUE.equals(r.getCookedSalesSuspended())
                    && !Objects.equals(r.getSuspensionInspectionId(), in.getId())) {
                // 已被另一送检单暂停并绑定：保留既有处置，不覆盖绑定关系
            } else {
                r.setCookedSalesSuspended(true);
                r.setSuspensionInspectionId(in.getId());
                r.setSuspensionReason(reasonBase + "，暂停熟食售卖待整改");
                r.setSuspensionUntil(null);
            }
            in.setCookedSuspended(true);
            r.setUpdatedBy(operator);
            r.setUpdatedAt(LocalDateTime.now());
            restrictionRepository.save(r);
        } else {
            // 合格：仅解除由本送检单引起的暂停，不扣保证金/信用，不影响抽签资格
            if (Objects.equals(r.getSuspensionInspectionId(), in.getId())) {
                r.setCookedSalesSuspended(false);
                r.setSuspensionReason(null);
                r.setSuspensionInspectionId(null);
                r.setSuspensionUntil(null);
            }
            in.setCookedSuspended(false);
            r.setUpdatedBy(operator);
            r.setUpdatedAt(LocalDateTime.now());
            restrictionRepository.save(r);
        }

        FoodInspection saved = inspectionRepository.save(in);
        auditService.log(operator.getId(), operator.getName(), "INSPECTION_RESULT",
                reasonBase + "：" + req.resultSummary()
                        + (in.getDepositDeducted() != null && in.getDepositDeducted().signum() > 0
                            ? "；已扣保证金 ¥" + in.getDepositDeducted() : "")
                        + (in.getCreditDelta() != null && in.getCreditDelta() != 0
                            ? "；信用 " + in.getCreditDelta() : "")
                        + (in.getDisqualifyCooked() ? "；取消熟食抽签资格" : ""));
        return saved;
    }

    /** 人工解除熟食售卖暂停（整改完成后；仅解除由本送检单引起的暂停） */
    @Transactional
    public FoodInspection liftSuspension(Long inspectionId, String note, User operator) {
        FoodInspection in = mustGet(inspectionId);
        VendorRestriction r = getOrCreate(in.getVendor());
        boolean causedByThis = Objects.equals(r.getSuspensionInspectionId(), in.getId());
        if (!causedByThis || !Boolean.TRUE.equals(r.getCookedSalesSuspended())) {
            throw new BizException("该送检单当前没有处于生效中的熟食售卖暂停");
        }
        r.setCookedSalesSuspended(false);
        r.setSuspensionReason(null);
        r.setSuspensionInspectionId(null);
        r.setSuspensionUntil(null);
        r.setUpdatedBy(operator);
        r.setUpdatedAt(LocalDateTime.now());
        restrictionRepository.save(r);

        in.setCookedSuspended(false);
        inspectionRepository.save(in);
        auditService.log(operator.getId(), operator.getName(), "INSPECTION_LIFT",
                "送检单#" + in.getId() + " 解除摊主[" + in.getVendor().getName() + "]熟食售卖暂停"
                        + (note != null && !note.isBlank() ? "：" + note : ""));
        return in;
    }

    // ================= 抽签资格判定 =================
    /** 摊主自己的送检单（摊主端协同用） */
    @Transactional(readOnly = true)
    public List<FoodInspection> myInspections(Long vendorId) {
        return inspectionRepository.findByVendorIdOrderByCreatedAtDesc(vendorId);
    }

    /** 摊主当前熟食售卖/抽签资格限制（无限制返回 null） */
    @Transactional(readOnly = true)
    public VendorRestriction myRestriction(Long vendorId) {
        return restrictionRepository.findByVendorId(vendorId).orElse(null);
    }

    /**
     * 该摊主在指定赶集日是否被取消熟食摊位抽签资格。
     * 仅由"检测不合格"结果触发；banUntil 为空表示永久，否则需赶集日不晚于截止日。
     */
    @Transactional(readOnly = true)
    public boolean cookedLotteryDisqualified(Long vendorId, LocalDate marketDate) {
        VendorRestriction r = restrictionRepository.findByVendorId(vendorId).orElse(null);
        if (r == null || !Boolean.TRUE.equals(r.getCookedLotteryBanned())) return false;
        return r.getBanUntil() == null || (marketDate != null && !marketDate.isAfter(r.getBanUntil()));
    }

    /** 送检未决期间是否暂停熟食售卖（供工作人员/摊主端提示与抽签参考） */
    @Transactional(readOnly = true)
    public boolean cookedSalesSuspended(Long vendorId) {
        return restrictionRepository.findByVendorId(vendorId)
                .map(VendorRestriction::getCookedSalesSuspended).orElse(false);
    }

    public VendorRestriction getOrCreate(User vendor) {
        return restrictionRepository.findByVendorId(vendor.getId()).orElseGet(() -> {
            VendorRestriction r = new VendorRestriction();
            r.setVendor(vendor);
            return restrictionRepository.save(r);
        });
    }

    private FoodInspection mustGet(Long id) {
        return inspectionRepository.findById(id)
                .orElseThrow(() -> new BizException("送检单不存在"));
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            throw new BizException("日期格式应为 yyyy-MM-dd");
        }
    }

    private String statusText(String s) {
        return Map.of("PENDING", "待送检", "IN_LAB", "检测中", "QUALIFIED", "检测合格",
                "UNQUALIFIED", "检测不合格", "CANCELLED", "已撤销").getOrDefault(s, s);
    }
}
