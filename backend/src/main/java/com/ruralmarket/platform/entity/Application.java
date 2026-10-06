package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "applications")
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private MarketEvent event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id", nullable = false)
    private User vendor;

    /** 经营品类：VEG_FRUIT 果蔬 / COOKED 熟食卤味 / PASTRY 糕点 / COLD_DISH 凉菜 /
     *  LIVE_POULTRY 活禽 / MEAT 鲜肉 / DAILY_GOODS 日用百货 / CLOTHING 服装 / OTHER 其他 */
    @Column(nullable = false, length = 30)
    private String category;

    /** 车辆尺寸（米） */
    private Double vehicleLength;
    private Double vehicleWidth;

    /** 是否需要用电 */
    @Column(nullable = false)
    private Boolean needsPower = false;

    /** 是否售卖熟食 */
    @Column(nullable = false)
    private Boolean sellsCookedFood = false;

    /** 是否售卖活禽 */
    @Column(nullable = false)
    private Boolean sellsLivePoultry = false;

    /** 证照照片，逗号分隔的 URL */
    @Column(length = 2000)
    private String licensePhotos;

    /** 历史经营记录自述 */
    @Column(length = 1000)
    private String historyRecord;

    /** PENDING 待审核 / APPROVED 已通过(待抽签) / REJECTED 已驳回 / WON 已中签 / LOST 未中签 / BLOCKED 资格受限不参与本次抽签 */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    /** BLOCKED 时的受限原因（如熟食抽检不合格，抽签资格冻结期内） */
    @Column(length = 500)
    private String blockReason;

    /** 报名时快照：是否为老摊主 */
    @Column(nullable = false)
    private Boolean veteran = false;

    @Column(length = 500)
    private String reviewNote;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
