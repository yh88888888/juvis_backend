package com.juvis.juvis.maintenance;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.juvis.juvis._core.enums.MaintenanceCategory;
import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis.user.User;

public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {

  @EntityGraph(attributePaths = {
      "branch",
      "requester",
      "vendor",
      "requestApprovedBy",
      "estimateApprovedBy"

      // photos는 여기서 굳이 안 가져와도 됨(아래에서 repo로 따로 조회하니까)
  })
  Optional<Maintenance> findDetailById(Long id);

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

  @Query("""
      select m
      from Maintenance m
      join fetch m.branch b
      left join fetch m.vendor v
      where m.id = :id
      """)
  Optional<Maintenance> findDetailForHq(@Param("id") Long id);

  // Vendor 목록 (List)
  List<Maintenance> findByVendor(User vendor);

  List<Maintenance> findByVendorAndStatus(User vendor, MaintenanceStatus status);

  // 필요하면 requester 기반도 유지
  List<Maintenance> findByRequester(User requester);

  // ✅ HQ 목록: 상태 필터(없으면 전체)
  List<Maintenance> findAllByOrderByCreatedAtDesc();

  List<Maintenance> findByStatusOrderByCreatedAtDesc(MaintenanceStatus status);

  // ✅ HQ 요약: 상태별 count
  long countByStatus(MaintenanceStatus status);

  long countByVendor_IdAndStatus(Long vendorId, MaintenanceStatus status);

  List<Maintenance> findByVendor_IdAndStatusOrderByCreatedAtDesc(
      Long vendorId,
      MaintenanceStatus status);

  List<Maintenance> findByVendor_IdAndStatusInOrderByCreatedAtDesc(
      Long vendorId,
      Collection<MaintenanceStatus> statuses);

  // ✅ summary count
  long countByVendorIdAndStatusIn(Integer vendorId, Collection<MaintenanceStatus> statuses);

  Optional<Maintenance> findByIdAndVendor_Id(Long id, Integer vendorId);

  @EntityGraph(attributePaths = { "branch", "requester" })
  @Query(value = """
      select m
      from Maintenance m
      where (:status is null or m.status = :status)
        and (:category is null or m.category = :category)
        and (:branchId is null or m.branch.id = :branchId)
        and (:from is null or m.createdAt >= :from)
        and (:to is null or m.createdAt < :to)
      """, countQuery = """
      select count(m)
      from Maintenance m
      where (:status is null or m.status = :status)
        and (:category is null or m.category = :category)
        and (:branchId is null or m.branch.id = :branchId)
        and (:from is null or m.createdAt >= :from)
        and (:to is null or m.createdAt < :to)
      """)
  Page<Maintenance> searchForOps(
      @Param("status") MaintenanceStatus status,
      @Param("category") MaintenanceCategory category,
      @Param("branchId") Long branchId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      Pageable pageable);

}
