package com.ruralmarket.platform.repository;

import com.ruralmarket.platform.entity.LabTest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LabTestRepository extends JpaRepository<LabTest, Long> {
    List<LabTest> findByEventIdOrderByCreatedAtDesc(Long eventId);
    List<LabTest> findByVendorIdOrderByCreatedAtDesc(Long vendorId);
    List<LabTest> findByComplaintId(Long complaintId);
    List<LabTest> findAllByOrderByCreatedAtDesc();
}
