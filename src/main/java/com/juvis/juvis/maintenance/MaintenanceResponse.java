package com.juvis.juvis.maintenance;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis.maintenance_estimate.MaintenanceEstimateAttempt;

public class MaintenanceResponse {

    @Getter
    @AllArgsConstructor
    public static class SimpleDTO {
        private Long id;
        private String branchName;
        private String requesterName;
        private BigDecimal estimateAmount;

        private LocalDate workStartDate;

        private String title;
        private MaintenanceStatus status;

        private LocalDateTime createdAt;
        private LocalDateTime submittedAt;

        public SimpleDTO(Maintenance m) {
            this.id = m.getId();
            this.branchName = m.getBranch() != null ? m.getBranch().getBranchName() : null;
            this.requesterName = m.getRequester() != null ? m.getRequester().getName() : null;
            this.estimateAmount = m.getEstimateAmount();
            this.workStartDate = m.getWorkStartDate();
            this.title = m.getTitle();
            this.status = m.getStatus();
            this.createdAt = m.getCreatedAt();
            this.submittedAt = m.getSubmittedAt();
        }
    }

    @Getter
    @AllArgsConstructor
    public static class DetailDTO {
        private Long id;

        private String branchName;
        private String branchAddress;

        private String requesterName;
        private String requesterPhone;

        private String title;
        private String description;
        private MaintenanceStatus status;

        public String category;
        public String categoryName;

        // ✅ 요청 첨부(지점 업로드)
        private List<String> requestPhotoUrls;

        // ✅ 완료 사진(벤더 작업완료 업로드)
        private List<String> resultPhotoUrls;

        // Vendor
        private String vendorName;
        private String vendorPhone;

        // 견적
        private BigDecimal estimateAmount;
        private String estimateComment;
        private LocalDate workStartDate;
        private LocalDate workEndDate;
        private List<EstimateAttemptDTO> estimateAttempts;

        private Integer estimateResubmitCount;

        // 결과
        private String resultComment;

        // (레거시) 단일 결과사진 URL - 기존 화면/코드 호환용으로 유지
        private String resultPhotoUrl;

        private LocalDateTime workCompletedAt;

        // ✅ 1차 결정(승인/반려 공통)
        private String requestApprovedByName;
        private LocalDateTime requestApprovedAt;
        private String requestApprovedComment; // ✅ HQ1 승인 코멘트

        // ✅ 2차 결정(승인/반려 공통)
        private String estimateApprovedByName;
        private LocalDateTime estimateApprovedAt;

        // ✅ 반려 사유(1/2차 분리)
        private String requestRejectedReason;
        private String estimateRejectedReason;

        private LocalDateTime createdAt;
        private LocalDateTime submittedAt;
        private LocalDateTime vendorSubmittedAt;

        public DetailDTO(
                Maintenance m,
                List<String> requestPhotoUrls,
                List<String> resultPhotoUrls,
                List<EstimateAttemptDTO> estimateAttempts) {
            this.id = m.getId();

            this.branchName = m.getBranch() != null ? m.getBranch().getBranchName() : null;
            this.branchAddress = m.getBranch() != null ? m.getBranch().getAddressName() : null;

            this.requesterName = m.getRequester() != null ? m.getRequester().getName() : null;
            this.requesterPhone = m.getRequester() != null ? m.getRequester().getPhone() : null;

            this.title = m.getTitle();
            this.description = m.getDescription();
            this.status = m.getStatus();
            this.category = m.getCategory().name();
            this.categoryName = m.getCategory().getDisplayName();

            // ✅ 분리된 사진 리스트
            this.requestPhotoUrls = (requestPhotoUrls == null) ? java.util.List.of() : requestPhotoUrls;
            this.resultPhotoUrls = (resultPhotoUrls == null) ? java.util.List.of() : resultPhotoUrls;

            if (m.getVendor() != null) {
                this.vendorName = m.getVendor().getName();
                this.vendorPhone = m.getVendor().getPhone();
            }

            // ✅ attempts 세팅 (null 방지)
            this.estimateAttempts = (estimateAttempts == null) ? java.util.List.of() : estimateAttempts;

            // =========================================================
            // ✅ 단일 견적 필드는 "항상 최신 attempt 기준"으로 채움
            // =========================================================
            EstimateAttemptDTO latest = null;
            if (this.estimateAttempts != null && !this.estimateAttempts.isEmpty()) {
                latest = this.estimateAttempts.get(this.estimateAttempts.size() - 1);
            }

            if (latest != null) {
                String amt = latest.getEstimateAmount();
                if (amt != null)
                    amt = amt.trim();

                if (amt == null || amt.isEmpty()) {
                    this.estimateAmount = null;
                } else {
                    this.estimateAmount = new java.math.BigDecimal(amt.replace(",", ""));
                }

                this.estimateComment = latest.getEstimateComment();
                this.workStartDate = latest.getWorkStartDate();
                this.workEndDate = latest.getWorkEndDate();
                this.vendorSubmittedAt = latest.getVendorSubmittedAt();

            } else {
                this.estimateAmount = m.getEstimateAmount();
                this.estimateComment = m.getEstimateComment();
                this.workStartDate = m.getWorkStartDate();
                this.workEndDate = m.getWorkEndDate();
                this.vendorSubmittedAt = m.getVendorSubmittedAt();
            }

            this.estimateResubmitCount = m.getEstimateResubmitCount();

            // 결과
            this.resultComment = m.getResultComment();
            this.workCompletedAt = m.getWorkCompletedAt();

            // ✅ 레거시 단일 URL은 "resultPhotoUrls 첫 번째" 우선, 없으면 엔티티 fallback
            if (this.resultPhotoUrls != null && !this.resultPhotoUrls.isEmpty()) {
                this.resultPhotoUrl = this.resultPhotoUrls.get(0);
            } else {
                this.resultPhotoUrl = m.getResultPhotoUrl();
            }

            // ✅ 1차 결정
            this.requestApprovedByName = m.getRequestApprovedBy() != null ? m.getRequestApprovedBy().getName() : null;
            this.requestApprovedAt = m.getRequestApprovedAt();

            // ✅✅✅ 여기 추가: HQ1 승인 코멘트 매핑
            this.requestApprovedComment = m.getRequestApprovedComment();

            // ✅ 2차 결정
            this.estimateApprovedByName = m.getEstimateApprovedBy() != null
                    ? m.getEstimateApprovedBy().getName()
                    : null;
            this.estimateApprovedAt = m.getEstimateApprovedAt();

            // ✅ 반려 사유
            this.requestRejectedReason = m.getRequestRejectedReason();
            this.estimateRejectedReason = m.getEstimateRejectedReason();

            this.createdAt = m.getCreatedAt();
            this.submittedAt = m.getSubmittedAt();
        }

        public static DetailDTO forBranch(
                Maintenance m,
                List<String> requestPhotoUrls,
                List<String> resultPhotoUrls,
                List<EstimateAttemptDTO> attempts) {
            DetailDTO dto = new DetailDTO(m, requestPhotoUrls, resultPhotoUrls, attempts);

            // ❌ 견적/2차결정 완전 제거
            dto.estimateAmount = null;
            dto.estimateComment = null;
            dto.estimateRejectedReason = null;
            dto.estimateApprovedByName = null;
            dto.estimateApprovedAt = null;
            dto.vendorSubmittedAt = null;
            dto.estimateResubmitCount = null;

            // ✅ 업체 이름/전화번호는 지점에 표시 (견적 제출 업체 연락 목적)
            // => 여기서는 절대 null로 지우지 않음

            // ✅ 작업예정일만 "진행 이후"에 표시 (정책)
            if (!(m.getStatus() == MaintenanceStatus.IN_PROGRESS
                    || m.getStatus() == MaintenanceStatus.COMPLETED)) {
                dto.workStartDate = null;
                dto.workEndDate = null;
            }

            return dto;
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EstimateAttemptDTO {

        private int attemptNo;
        private String estimateAmount;
        private String estimateComment;
        private LocalDate workStartDate;
        private LocalDate workEndDate;
        private LocalDateTime vendorSubmittedAt;

        // ✅ 추가: attempt별 견적 사진 URL
        private List<String> estimatePhotoUrls = List.of();

        private String hqDecision;
        private LocalDateTime hqDecidedAt;
        private String hqDecidedByName;
        private String hqRejectReason;

        // ✅ 기존 유지 (다른 곳에서 쓰고 있으면 안 깨짐)
        public static EstimateAttemptDTO from(MaintenanceEstimateAttempt a) {
            return from(a, List.of());
        }

        // ✅ 신규: attempt별 사진을 주입해서 DTO 생성
        public static EstimateAttemptDTO from(MaintenanceEstimateAttempt a, List<String> estimatePhotoUrls) {
            EstimateAttemptDTO dto = new EstimateAttemptDTO();
            dto.setAttemptNo(a.getAttemptNo());
            dto.setEstimateAmount(a.getEstimateAmount());
            dto.setEstimateComment(a.getEstimateComment());
            dto.setWorkStartDate(a.getWorkStartDate());
            dto.setWorkEndDate(a.getWorkEndDate());
            dto.setVendorSubmittedAt(a.getVendorSubmittedAt());

            dto.setEstimatePhotoUrls(
                    estimatePhotoUrls == null ? List.of() : estimatePhotoUrls);

            dto.setHqDecision(a.getHqDecision() == null ? "PENDING" : a.getHqDecision().name());
            dto.setHqDecidedAt(a.getHqDecidedAt());
            dto.setHqDecidedByName(a.getHqDecidedByName());
            dto.setHqRejectReason(a.getHqRejectReason());
            return dto;
        }
    }
}
