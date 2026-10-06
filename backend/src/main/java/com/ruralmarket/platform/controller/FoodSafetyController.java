package com.ruralmarket.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruralmarket.platform.config.AuthUser;
import com.ruralmarket.platform.config.BizException;
import com.ruralmarket.platform.entity.*;
import com.ruralmarket.platform.repository.*;
import com.ruralmarket.platform.service.LabTestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理端：食品投诉案件关联、留样送检、熟食售卖暂停与抽签资格管控。
 */
@RestController
@RequestMapping("/api/admin")
public class FoodSafetyController {
    private final LabTestService labTestService;
    private final LabTestRepository labTestRepository;
    private final SalesSuspensionRepository suspensionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public FoodSafetyController(LabTestService labTestService, LabTestRepository labTestRepository,
                                SalesSuspensionRepository suspensionRepository, UserRepository userRepository) {
        this.labTestService = labTestService;
        this.labTestRepository = labTestRepository;
        this.suspensionRepository = suspensionRepository;
        this.userRepository = userRepository;
    }

    private User me() {
        return userRepository.findById(AuthUser.current().id()).orElseThrow();
    }

    // ================= 投诉案件关联 =================

    /** 案件聚合：留样 / 购买时间 / 支付凭证 / 同批次消费者 / 巡查 / 入场核验 / 缴费 / 既有送检 */
    @GetMapping("/complaints/{id}/case")
    public Map<String, Object> caseBundle(@PathVariable Long id) {
        return labTestService.buildCase(id);
    }

    // ================= 留样送检 =================

    @GetMapping("/lab-tests")
    public List<LabTest> labTests(@RequestParam(required = false) Long eventId,
                                  @RequestParam(required = false) String status) {
        List<LabTest> list = labTestRepository.findAllByOrderByCreatedAtDesc();
        return list.stream()
                .filter(t -> eventId == null || t.getEvent().getId().equals(eventId))
                .filter(t -> status == null || status.isBlank() || status.equals(t.getStatus()))
                .toList();
    }

    @GetMapping("/lab-tests/{id}")
    public Map<String, Object> labTest(@PathVariable Long id) {
        LabTest t = labTestRepository.findById(id)
                .orElseThrow(() -> new BizException("送检单不存在"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("labTest", t);
        out.put("case", labTestService.buildCase(t.getComplaint().getId()));
        out.put("suspensions", suspensionRepository.findByVendorIdOrderByCreatedAtDesc(t.getVendor().getId()));
        out.put("salesList", parseSalesList(t.getSample()));
        return out;
    }

    private List<?> parseSalesList(FoodSample s) {
        if (s == null || s.getSalesList() == null || s.getSalesList().isBlank()) return List.of();
        try {
            return mapper.readValue(s.getSalesList(), List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @PostMapping("/lab-tests")
    public LabTest create(@Valid @RequestBody LabTestService.CreateReq req) {
        return labTestService.create(req, me());
    }

    @PostMapping("/lab-tests/{id}/send")
    public LabTest send(@PathVariable Long id, @RequestBody(required = false) LabTestService.SendReq req) {
        return labTestService.send(id, req == null ? new LabTestService.SendReq(null, null, null, null) : req, me());
    }

    @PostMapping("/lab-tests/{id}/result")
    public LabTest result(@PathVariable Long id, @Valid @RequestBody LabTestService.ResultReq req) {
        return labTestService.reportResult(id, req, me());
    }

    // ================= 熟食售卖暂停 / 抽签资格冻结 =================

    @GetMapping("/suspensions")
    public List<SalesSuspension> suspensions(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) Long vendorId) {
        List<SalesSuspension> list = status == null || status.isBlank()
                ? suspensionRepository.findAll()
                : suspensionRepository.findByStatusOrderByCreatedAtDesc(status);
        return list.stream()
                .filter(s -> vendorId == null || s.getVendor().getId().equals(vendorId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
    }

    @PostMapping("/suspensions")
    public SalesSuspension suspend(@Valid @RequestBody LabTestService.SuspendReq req) {
        return labTestService.suspend(req, me());
    }

    public record RevokeReq(String reason) {}

    @PostMapping("/suspensions/{id}/revoke")
    public SalesSuspension revoke(@PathVariable Long id, @RequestBody(required = false) RevokeReq req) {
        String reason = req == null || req.reason() == null || req.reason().isBlank()
                ? "管理员人工解除" : req.reason();
        return labTestService.revokeSuspension(id, reason, me());
    }

    /** 查询摊主在指定日期的熟食售卖/抽签资格状态 */
    @GetMapping("/vendors/{id}/eligibility")
    public Map<String, Object> eligibility(@PathVariable Long id,
                                           @RequestParam(required = false) String date) {
        return labTestService.eligibility(id, date);
    }
}
