package com.ruralmarket.platform.repository;

import com.ruralmarket.platform.entity.FoodInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FoodInspectionRepository extends JpaRepository<FoodInspection, Long> {
    List<FoodInspection> findByEventIdOrderByCreatedAtDesc(Long eventId);
    List<FoodInspection> findByVendorIdOrderByCreatedAtDesc(Long vendorId);
    Optional<FoodInspection> findByComplaintId(Long complaintId);
    List<FoodInspection> findBySampleId(Long sampleId);
    List<FoodInspection> findByBatchNo(String batchNo);
    /** 送检期间/待结果的未决送检（用于熟食售卖暂停与抽签拦截） */
    List<FoodInspection> findByVendorIdAndStatusIn(Long vendorId, List<String> statuses);
}
