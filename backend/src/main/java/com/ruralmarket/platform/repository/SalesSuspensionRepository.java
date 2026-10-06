package com.ruralmarket.platform.repository;

import com.ruralmarket.platform.entity.SalesSuspension;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SalesSuspensionRepository extends JpaRepository<SalesSuspension, Long> {
    List<SalesSuspension> findByVendorIdOrderByCreatedAtDesc(Long vendorId);
    List<SalesSuspension> findByVendorIdAndStatusOrderByCreatedAtDesc(Long vendorId, String status);
    List<SalesSuspension> findByStatusOrderByCreatedAtDesc(String status);
}
