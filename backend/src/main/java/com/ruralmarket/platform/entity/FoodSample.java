package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 食品留样记录：卤味、糕点、凉菜等食品摊主上传。
 */
@Data
@Entity
@Table(name = "food_samples")
public class FoodSample {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private MarketEvent event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id", nullable = false)
    private User vendor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "result_id")
    private LotteryResult result;

    /** 食品名称 */
    @Column(nullable = false, length = 100)
    private String productName;

    /** 批次 */
    @Column(nullable = false, length = 50)
    private String batchNo;

    /** 留样照片 */
    @Column(nullable = false, length = 1000)
    private String photo;

    /** 保存位置（如：1号冷藏柜） */
    @Column(nullable = false, length = 200)
    private String storageLocation;

    /** KEPT 已留样 / DISPOSED 已处置 / ABNORMAL 异常 / LAB_TESTING 送检中 / LAB_SENT 已送实验室 / DESTROYED 已销毁 */
    @Column(nullable = false, length = 20)
    private String status = "KEPT";

    /** 送检管控期间该批次食品在售状态：ON_SALE 在售 / REMOVED 已下架 / SOLD_OUT 已售罄 / KEPT_LIST 留存销售名单 */
    @Column(nullable = false, length = 20)
    private String sellingStatus = "ON_SALE";

    /** 摊主登记的同批次销售名单（JSON：[{buyer,phone,qty,soldAt,note}]） */
    @Column(length = 4000)
    private String salesList;

    /** 摊主下架/留存名单的确认说明 */
    @Column(length = 1000)
    private String salesListNote;

    private LocalDateTime salesListUpdatedAt;

    @Column(length = 500)
    private String remark;

    @Column(nullable = false)
    private LocalDateTime retainedAt = LocalDateTime.now();
}
