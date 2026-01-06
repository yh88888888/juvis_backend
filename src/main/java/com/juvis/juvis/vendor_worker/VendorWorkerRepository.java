package com.juvis.juvis.vendor_worker;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorWorkerRepository extends JpaRepository<VendorWorker, Long> {
    List<VendorWorker> findByVendorIdAndIsActiveTrueOrderByIdDesc(Long vendorId);
    Optional<VendorWorker> findByIdAndVendorId(Long id, Long vendorId);
    Optional<VendorWorker> findByIdAndVendorIdAndIsActiveTrue(Long id, Long vendorId);
}
