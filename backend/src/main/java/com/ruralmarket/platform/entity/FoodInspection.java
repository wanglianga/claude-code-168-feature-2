package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 食品留样送检单：由食品安全投诉（腹泻等）发起，关联摊主留样送检。
 * 送检期间暂停该摊主熟食售卖，并要求对同批次食品下架或留存销售名单；
 * 检测结果出来后联动保证金扣罚、信用扣分与后续熟食摊位抽签资格。
 */
@Data
@Entity
@Table(name = "food_inspections")
public class FoodInspection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private MarketEvent event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id", nullable = false)
    private User vendor;

    /** 触发送检的投诉 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    /** 被送检的留样（同批次留样） */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sample_id", nullable = false)
    private FoodSample sample;

    /** 涉及的摊位分配单（用于定位批次销售摊位，可空） */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "result_id")
    private LotteryResult result;

    /** 同批次号（冗余留档，取自留样批次） */
    @Column(nullable = false, length = 50)
    private String batchNo;

    /** 承检机构 */
    @Column(nullable = false, length = 200)
    private String labName;

    /** 检测项目，如 菌落总数/大肠菌群/沙门氏菌，逗号分隔 */
    @Column(length = 500)
    private String testItems;

    /** PENDING 待送检 / IN_LAB 检测中 / QUALIFIED 检测合格 / UNQUALIFIED 检测不合格 / CANCELLED 已撤销 */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    /** 送检期间要求：REMOVE 同批次下架 / KEEP_LIST 留存销售名单继续观察 */
    @Column(nullable = false, length = 20)
    private String batchAction = "REMOVE";

    /** 送检期间是否暂停该摊主熟食售卖 */
    @Column(nullable = false)
    private Boolean cookedSuspended = true;

    /** 摊主对同批次处置的回执说明（下架照片/销售名单） */
    @Column(length = 2000)
    private String batchReceipt;

    /** 摊主回执处置方式：REMOVED 已下架 / LIST_KEPT 已留存名单 */
    @Column(length = 20)
    private String batchReceiptStatus;

    private LocalDateTime batchReceiptAt;

    /** 检测结果说明 */
    @Column(length = 2000)
    private String resultSummary;

    /** 检测报告照片（逗号分隔） */
    @Column(length = 2000)
    private String reportPhoto;

    /** 结果登记时实际扣罚的保证金金额（0 表示未扣） */
    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal depositDeducted = java.math.BigDecimal.ZERO;

    /** 结果登记时实际信用变动（负为扣分，0 不变） */
    @Column
    private Integer creditDelta = 0;

    /** 结果处理：是否取消后续熟食摊位抽签资格 */
    @Column(nullable = false)
    private Boolean disqualifyCooked = false;

    /** 不合格时取消熟食资格的截止日期（null 表示永久，合格则不适用） */
    private java.time.LocalDate disqualifyUntil;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "result_by")
    private User resultBy;

    private LocalDateTime resultAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
