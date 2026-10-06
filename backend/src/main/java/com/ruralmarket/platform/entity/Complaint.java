package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 投诉与申诉：消费者投诉（腹泻/价格/缺斤少两等）+ 摊主申诉（扣罚争议等）。
 */
@Data
@Entity
@Table(name = "complaints")
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private MarketEvent event;

    /** 被投诉的摊位分配单（摊主申诉时可为空） */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "result_id")
    private LotteryResult result;

    /** 被投诉/申诉涉及的摊主 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id")
    private User vendor;

    /** CONSUMER 消费者投诉 / VENDOR 摊主申诉 */
    @Column(nullable = false, length = 20)
    private String reporterType = "CONSUMER";

    /** 投诉人姓名（摊主申诉时=摊主本人） */
    @Column(nullable = false, length = 50)
    private String reporterName;

    @Column(length = 20)
    private String reporterPhone;

    /** DIARRHEA 腹泻食品安全 / PRICE 价格 / SHORT_WEIGHT 缺斤少两 / HYGIENE 卫生 /
     *  QUALITY 质量 / BOUNDARY 摊位边界纠纷 / FEE_DISPUTE 费用争议 /
     *  DEPOSIT_DISPUTE 保证金扣罚争议 / OTHER 其他 */
    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 2000)
    private String description;

    /** 消费者购买时间（食品安全投诉用于锁定留样批次与同批次消费者） */
    private LocalDateTime purchaseTime;

    /** 支付凭证照片（微信/支付宝/收据，逗号分隔） */
    @Column(length = 2000)
    private String paymentProof;

    /** SUBMITTED 已提交 / PROCESSING 处理中 / RESOLVED 已办结 / REJECTED 已驳回 */
    @Column(nullable = false, length = 20)
    private String status = "SUBMITTED";

    /** 处理结果说明（向投诉人/摊主公开） */
    @Column(length = 2000)
    private String resolution;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "handler_id")
    private User handler;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime handledAt;
}
