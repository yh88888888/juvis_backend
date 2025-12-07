package com.juvis.juvis.maintenance;

import com.juvis.juvis._core.enums.MaintenanceCategory;
import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {

    @Query("""
            SELECT m
            FROM Maintenance m
            WHERE (:status IS NULL OR m.status = :status)
              AND (:category IS NULL OR m.category = :category)
              AND (:branchId IS NULL OR m.branch.id = :branchId)
            ORDER BY m.createdAt DESC
            """)
    List<Maintenance> searchForHq(
            @Param("status") MaintenanceStatus status,
            @Param("category") MaintenanceCategory category,
            @Param("branchId") Long branchId);

    List<Maintenance> findForVendor(User currentUser, String statusStr, String categoryStr);

    List<Maintenance> findMyRequests(User currentUser, String statusStr, String categoryStr);

    // 공통
    // Optional<Maintenance> findById(Long id); // JpaRepository가 이미 제공

    // BRANCH
    List<Maintenance> findByRequester(User requester);

    // HQ
    List<Maintenance> findByStatus(MaintenanceStatus status);

    List<Maintenance> findByBranch_Id(Long branchId);

    List<Maintenance> findByStatusAndBranch_Id(MaintenanceStatus status, Long branchId);

    // VENDOR
    List<Maintenance> findByVendor(User vendor);

    List<Maintenance> findByVendorAndStatus(User vendor, MaintenanceStatus status);
}
