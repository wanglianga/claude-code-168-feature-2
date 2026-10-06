package com.ruralmarket.platform.controller;

import com.ruralmarket.platform.config.BizException;
import com.ruralmarket.platform.entity.*;
import com.ruralmarket.platform.repository.*;
import jakarta.validation.constraints.NotBlank;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 消费者投诉（公开，无需登录）：凭摊位号投诉 */
@RestController
@RequestMapping("/api/complaints")
public class PublicComplaintController {
    private final ComplaintRepository complaintRepository;
    private final LotteryResultRepository resultRepository;
    private final MarketEventRepository eventRepository;

    public PublicComplaintController(ComplaintRepository complaintRepository,
                                     LotteryResultRepository resultRepository,
                                     MarketEventRepository eventRepository) {
        this.complaintRepository = complaintRepository;
        this.resultRepository = resultRepository;
        this.eventRepository = eventRepository;
    }

    public record PublicComplaintReq(Long eventId, @NotBlank String stallNo,
                                     @NotBlank String reporterName, String reporterPhone,
                                     @NotBlank String type, @NotBlank String description,
                                     String foodName, String purchaseTime,
                                     String paymentProof, String batchNo) {}

    @PostMapping("/public")
    @Transactional
    public Map<String, Object> submit(@RequestBody PublicComplaintReq req) {
        MarketEvent event;
        if (req.eventId() != null) {
            event = eventRepository.findById(req.eventId()).orElseThrow(() -> new BizException("集市不存在"));
        } else {
            // 默认最近一场已抽签或进行中的集市
            List<MarketEvent> events = eventRepository.findAllByOrderByMarketDateDesc();
            event = events.stream()
                    .filter(e -> List.of("LOTTERY_DONE", "IN_PROGRESS").contains(e.getStatus()))
                    .findFirst()
                    .orElseThrow(() -> new BizException("当前没有进行中的集市"));
        }
        LotteryResult result = resultRepository.findByApplicationEventId(event.getId()).stream()
                .filter(r -> r.getStallNo().equalsIgnoreCase(req.stallNo().trim()))
                .findFirst()
                .orElseThrow(() -> new BizException("该集市下不存在摊位号 " + req.stallNo()));

        Complaint c = new Complaint();
        c.setEvent(event);
        c.setResult(result);
        c.setVendor(result.getApplication().getVendor());
        c.setReporterType("CONSUMER");
        c.setReporterName(req.reporterName());
        c.setReporterPhone(req.reporterPhone());
        c.setType(req.type());
        c.setDescription(req.description());
        c.setFoodName(req.foodName());
        if (req.purchaseTime() != null && !req.purchaseTime().isBlank()) {
            c.setPurchaseTime(LocalDateTime.parse(req.purchaseTime()));
        }
        c.setPaymentProof(req.paymentProof());
        c.setBatchNo(req.batchNo());
        complaintRepository.save(c);
        return Map.of("message", "投诉已提交，市场管理方将尽快处理",
                "complaintId", c.getId(), "event", event.getName());
    }
}
