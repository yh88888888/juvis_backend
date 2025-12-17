package com.juvis.juvis.maintenance;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.juvis.juvis._core.enums.MaintenanceCategory;
import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis.user.User;

public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {

  // HQ 검색 (Page) - 엔티티 조회
  @Query(value = """
      select distinct m
      from Maintenance m
      join fetch m.branch b
      join fetch m.requester r
      where (:status is null or m.status = :status)
        and (:category is null or m.category = :category)
        and (:branchId is null or b.id = :branchId)
      """, countQuery = """
      select count(m)
      from Maintenance m
      where (:status is null or m.status = :status)
        and (:category is null or m.category = :category)
        and (:branchId is null or m.branch.id = :branchId)
      """)
  Page<Maintenance> searchForHq(
      @Param("status") MaintenanceStatus status,
      @Param("category") MaintenanceCategory category,
      @Param("branchId") Long branchId,
      Pageable pageable);

  // Branch 검색 (Page) - 엔티티 조회
  @Query(value = """
      select m
      from Maintenance m
      join fetch m.branch b
      join fetch m.requester r
      where b.id = :branchId
        and (:status is null or m.status = :status)
        and (:category is null or m.category = :category)
      """, countQuery = """
        select count(m)
        from Maintenance m
        where m.branch.id = :branchId
          and (:status is null or m.status = :status)
          and (:category is null or m.category = :category)
      """)
  Page<Maintenance> searchForBranch(
      @Param("branchId") Long branchId,
      @Param("status") MaintenanceStatus status,
      @Param("category") MaintenanceCategory category,
      Pageable pageable);

  // Vendor 목록 (List)
  List<Maintenance> findByVendor(User vendor);

  List<Maintenance> findByVendorAndStatus(User vendor, MaintenanceStatus status);

  // 필요하면 requester 기반도 유지
  List<Maintenance> findByRequester(User requester);
}
