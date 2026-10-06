package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 熟食售卖暂停/熟食摊位抽签资格冻结记录。
 * 留样送检结果不合格或调查期间由管理员发起；
 * 暂停期内摊主不得售卖熟食凉菜，冻结期内不得参与熟食摊位抽签。
 */
@Data
@Entity
@Table(name = "sales_suspensions")
public class SalesSuspension {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id", nullable = false)
    private User vendor;

    /** 关联集市（可空，表示跨集市的处罚） */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id")
    private MarketEvent event;

    /** 关联送检单（调查期间临时暂停时可空） */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lab_test_id")
    private LabTest labTest;

    /**
     * SALES_SUSPEND 暂停熟食售卖 / LOTTERY_BAN 熟食抽签资格冻结 / BOTH 两者并处
     */
    @Column(nullable = false, length = 20)
    private String type;

    /** ACTIVE 生效中 / REVOKED 已解除 / EXPIRED 已到期 */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private LocalDate startDate;

    /** null 表示长期（至人工解除） */
    private LocalDate endDate;

    /** 暂停原因（必填，向摊主公开） */
    @Column(nullable = false, length = 1000)
    private String reason;

    /** INVESTIGATING 送检调查期间 / LAB_UNQUALIFIED 检测不合格 / MANUAL 管理员决定 */
    @Column(nullable = false, length = 20)
    private String reasonType;

    /** 来源单据ID（送检单/投诉ID） */
    private Long sourceRefId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    /** 解除人/解除说明 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "revoked_by")
    private User revokedBy;

    @Column(length = 1000)
    private String revokeReason;

    private LocalDateTime revokedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
