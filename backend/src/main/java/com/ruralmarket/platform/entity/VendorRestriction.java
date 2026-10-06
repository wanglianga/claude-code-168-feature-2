package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 摊主熟食经营限制（送检处置的当前状态）。
 * - cookedSalesSuspended：暂停熟食售卖（送检期间或检测不合格），解除后可恢复；
 * - cookedLotteryBanned：取消后续熟食摊位抽签资格（检测不合格），到期或人工解除前不分配冷链熟食摊位。
 * FoodInspection 留全量处置历史，本表只存当前生效状态，便于报名审核与抽签规则读取。
 */
@Data
@Entity
@Table(name = "vendor_restrictions")
public class VendorRestriction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id", nullable = false, unique = true)
    private User vendor;

    /** 是否暂停熟食售卖 */
    @Column(nullable = false)
    private Boolean cookedSalesSuspended = false;

    @Column(length = 500)
    private String suspensionReason;

    private Long suspensionInspectionId;

    /** 暂停截止日期（null 表示未明确期限，需人工解除） */
    private LocalDate suspensionUntil;

    /** 是否取消熟食摊位抽签资格 */
    @Column(nullable = false)
    private Boolean cookedLotteryBanned = false;

    @Column(length = 500)
    private String banReason;

    private Long banInspectionId;

    /** 抽签资格取消截止日期（null=永久取消） */
    private LocalDate banUntil;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
