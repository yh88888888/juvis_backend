package com.juvis.juvis.maintenance;

import com.juvis.juvis._core.enums.MaintenanceCategory;
import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis.user.User;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {

  // HQ 검색 (Page)
  @Query("""
      select new com.juvis.juvis.maintenance.MaintenanceResponse$SimpleDTO(
          m.id,
          m.branchSeq,
          b.branchName,
          r.name,
          m.title,
          m.status,
          m.createdAt,
          m.submittedAt
      )
      from Maintenance m
      join m.branch b
      left join m.requester r
      where (:status is null or m.status = :status)
        and (:category is null or m.category = :category)
        and (:branchId is null or b.id = :branchId)
      """)
  Page<Maintenance> searchForHq(
      @Param("status") MaintenanceStatus status,
      @Param("category") MaintenanceCategory category,
      @Param("branchId") Long branchId,
      Pageable pageable);

  // Branch 검색 (Page)
  @Query("""
      select new com.juvis.juvis.maintenance.dto.MaintenanceListDTO(
          m.id,
          m.title,
          m.status,
          m.urgency,
          m.createdAt,
          b.id,
          b.branchName
      )
      from Maintenance m
      join m.branch b
      where (:status is null or m.status = :status)
        and (:category is null or m.category = :category)
        and m.branch.id = :branchId
      """)
  Page<Maintenance> searchForBranch(
      @Param("branchId") Long branchId,
      @Param("status") MaintenanceStatus status,
      @Param("category") MaintenanceCategory category,
      Pageable pageable);

  // (선택) Vendor 검색 (Page) — 지금은 Service에서 List 파생 쿼리로 처리하므로 사용 안 해도 됨.
  @Query("""
      select new com.juvis.juvis.maintenance.dto.MaintenanceListDTO(
          m.id,
          m.title,
          m.status,
          m.urgency,
          m.createdAt,
          b.id,
          b.branchName
      )
      from Maintenance m
      join m.branch b
      where m.vendorUser.id = :vendorUserId
        and (:status is null or m.status = :status)
        and (:category is null or m.category = :category)
      """)
  Page<Maintenance> searchForVendor(@Param("vendorUserId") Long vendorUserId,
      @Param("status") MaintenanceStatus status,
      @Param("category") MaintenanceCategory category,
      Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT COALESCE(MAX(m.branchSeq), 0)
      FROM Maintenance m
      WHERE m.branch.id = :branchId
      """)
  Integer findMaxBranchSeqByBranchId(@Param("branchId") Long branchId);

  // 파생 쿼리
  List<Maintenance> findByRequester(User requester);

  List<Maintenance> findByStatus(MaintenanceStatus status);

  List<Maintenance> findByBranch_Id(Long branchId);

  List<Maintenance> findByStatusAndBranch_Id(MaintenanceStatus status, Long branchId);

  List<Maintenance> findByVendor(User vendor);

  List<Maintenance> findByVendorAndStatus(User vendor, MaintenanceStatus status);

  // ❌ 제거: 스프링 규칙에도 안 맞고 구현도 없음
  // List<Maintenance> findForVendor(User currentUser, String statusStr, String
  // categoryStr);
  // List<Maintenance> findMyRequests(User currentUser, String statusStr, String
  // categoryStr);
}