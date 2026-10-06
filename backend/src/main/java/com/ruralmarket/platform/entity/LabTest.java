package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 留样送检单：食品安全投诉触发，关联投诉、摊主留样与批次。
 * 送检期间对同批次食品采取下架或留存销售名单措施；
 * 检测结果不合格时联动保证金扣罚、信用扣分、暂停熟食售卖及后续抽签资格限制。
 */
@Data
@Entity
@Table(name = "lab_tests")
public class LabTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private MarketEvent event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id", nullable = false)
    private User vendor;

    /** 触发送检的主投诉（消费者腹泻等食品安全投诉） */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    /** 送检的留样（同批次留样，可空——摊主未留样也要送检现场抽样） */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sample_id")
    private FoodSample sample;

    /** 受检食品名称 */
    @Column(nullable = false, length = 100)
    private String productName;

    /** 批次号 */
    @Column(nullable = false, length = 50)
    private String batchNo;

    /** 检测机构 */
    @Column(length = 100)
    private String labName;

    /** 检测项目（菌落总数/大肠菌群/沙门氏菌等，逗号分隔） */
    @Column(length = 500)
    private String testItems;

    /** 抽样人（管理员/工作人员） */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sampled_by")
    private User sampledBy;

    private LocalDateTime sampledAt;

    /** 实验室收样日期 */
    private LocalDate receivedDate;

    /**
     * INITIATED 已发起待抽样 / SENT 已送检测机构 /
     * QUALIFIED 检测合格 / UNQUALIFIED 检测不合格 / CANCELLED 已撤销
     */
    @Column(nullable = false, length = 20)
    private String status = "INITIATED";

    /**
     * 送检期间对同批次在售食品的管控：
     * REMOVE 要求立即下架 / KEEP_LIST 可继续售卖但必须留存购买者名单
     */
    @Column(nullable = false, length = 20)
    private String batchControl = "REMOVE";

    /** 管控通知说明（向摊主公开） */
    @Column(length = 1000)
    private String controlNotice;

    /** 检测结果说明 */
    @Column(length = 2000)
    private String resultSummary;

    /** 检测报告照片（逗号分隔 URL） */
    @Column(length = 2000)
    private String reportPhotos;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    private LocalDateTime reportedAt;

    /** 结果处理：保证金扣罚金额（0 表示未扣罚） */
    @Column(nullable = false)
    private Integer depositPenalty = 0;

    /** 结果处理：信用扣分（负数，0 表示不扣） */
    @Column(nullable = false)
    private Integer creditDelta = 0;

    /** 结果处理：暂停熟食售卖天数（0 表示不暂停） */
    @Column(nullable = false)
    private Integer suspendDays = 0;

    /** 结果处理：后续熟食摊位抽签资格冻结天数（0 表示不冻结） */
    @Column(nullable = false)
    private Integer lotteryBanDays = 0;

    /** 结果处理人 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "result_handler_id")
    private User resultHandler;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
