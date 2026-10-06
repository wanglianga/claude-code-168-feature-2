package com.ruralmarket.platform.repository;

import com.ruralmarket.platform.entity.VendorRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VendorRestrictionRepository extends JpaRepository<VendorRestriction, Long> {
    Optional<VendorRestriction> findByVendorId(Long vendorId);
}
