package com.juvis.juvis.maintenance;

import com.juvis.juvis._core.enums.MaintenanceCategory;
import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis.branch.Branch;
import com.juvis.juvis.maintenance_photo.MaintenancePhoto;
import com.juvis.juvis.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "maintenance_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Maintenance {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   @Column(name = "request_id")
   private Long id;

   // 지점
   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "branch_id", nullable = false)
   private Branch branch;

   // 요청자 (지점 사용자)
   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "requester_id", nullable = false)
   private User requester;

   // 선택된 벤더 (업체 사용자)
   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "vendor_id")
   private User vendor;

   /*
   * =======================
   * 기본 요청 정보
   * =======================
   */
   @Column(length = 200, nullable = false)
   private String title;

   @Lob
   private String description;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 20)
   private MaintenanceStatus status;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 30)
   private MaintenanceCategory category;

   @OneToMany(mappedBy = "maintenance", cascade = CascadeType.ALL, orphanRemoval = true)
   @OrderBy("id ASC")
   private List<MaintenancePhoto> photos = new ArrayList<>();

   /*
   * =======================
   * 견적 / 작업 일정
   * =======================
   */
   @Column(name = "estimate_amount", precision = 15, scale = 2)
   private BigDecimal estimateAmount;

   @Lob
   @Column(name = "estimate_comment")
   private String estimateComment;

   @Column(name = "work_start_date")
   private LocalDate workStartDate;

   @Column(name = "work_end_date")
   private LocalDate workEndDate;

   @Column(name = "estimate_resubmit_count", nullable = false)
   private int estimateResubmitCount;

   /*
   * =======================
   * 작업 결과
   * =======================
   */
   @Lob
   @Column(name = "result_comment")
   private String resultComment;

   @Column(name = "result_photo_url")
   private String resultPhotoUrl;

   @Column(name = "work_completed_at")
   private LocalDateTime workCompletedAt;

   /*
   * =======================
   * ✅ 반려 사유(1차/2차 분리)
   * =======================
   */
   @Column(name = "request_rejected_reason", length = 500)
   private String requestRejectedReason;

   @Column(name = "estimate_rejected_reason", length = 500)
   private String estimateRejectedReason;

   /*
   * =======================
   * ✅ 결정 기록(승인/반려 공통, 1차/2차 분리)
   * =======================
   */
   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "request_approved_by")
   private User requestApprovedBy;

   @Column(name = "request_approved_at")
   private LocalDateTime requestApprovedAt;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "estimate_approved_by")
   private User estimateApprovedBy;

   @Column(name = "estimate_approved_at")
   private LocalDateTime estimateApprovedAt;

   /*
   * =======================
   * 프로세스 타임스탬프
   * =======================
   */
   @Column(name = "submitted_at")
   private LocalDateTime submittedAt;

   @Column(name = "vendor_submitted_at")
   private LocalDateTime vendorSubmittedAt;

   @CreationTimestamp
   private LocalDateTime createdAt;

   @UpdateTimestamp
   private LocalDateTime updatedAt;

   // -----------------------
   // 생성 헬퍼
   // -----------------------
   public static Maintenance createDraft(Branch branch, User requester, MaintenanceRequest.CreateDTO dto) {
      return Maintenance.builder()
               .branch(branch)
               .requester(requester)
               .title(dto.getTitle())
               .description(dto.getDescription())
               .category(dto.getCategory())
               .status(MaintenanceStatus.DRAFT)
               .estimateResubmitCount(0)
               .submittedAt(null)
               .build();
   }

   public static Maintenance createSubmitted(Branch branch, User requester, MaintenanceRequest.CreateDTO dto) {
      return Maintenance.builder()
               .branch(branch)
               .requester(requester)
               .title(dto.getTitle())
               .description(dto.getDescription())
               .category(dto.getCategory())
               .status(MaintenanceStatus.REQUESTED)
               .submittedAt(LocalDateTime.now())
               .estimateResubmitCount(0)
               .build();
   }
}
