package com.juvis.juvis.maintenance;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.juvis.juvis._core.enums.MaintenanceStatus;

public class MaintenanceResponse {

    @Getter
    @AllArgsConstructor
    public static class SimpleDTO {
        private Long id;
        private Integer branchSeq; 
        private String branchName;
        private String requesterName;

        private String title;
        private MaintenanceStatus status;

        private LocalDateTime createdAt;
        private LocalDateTime submittedAt;

        public SimpleDTO(Maintenance m) {
            this.id = m.getId();
            this.branchName = m.getBranch() != null ? m.getBranch().getBranchName() : null;
            this.requesterName = m.getRequester() != null ? m.getRequester().getName() : null;
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

        // Vendor
        private String vendorName;
        private String vendorPhone;

        // 견적
        private BigDecimal estimateAmount;
        private String estimateComment;
        private LocalDate workStartDate;
        private LocalDate workEndDate;

        // 결과
        private String resultComment;
        private String resultPhotoUrl;
        private LocalDateTime workCompletedAt;

        // 승인
        private String approvedByName;
        private LocalDateTime approvedAt;

        private LocalDateTime createdAt;
        private LocalDateTime submittedAt;
        private LocalDateTime vendorSubmittedAt;
        private String rejectedReason;

        public DetailDTO(Maintenance m) {
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

            if (m.getVendor() != null) {
                this.vendorName = m.getVendor().getName();
                this.vendorPhone = m.getVendor().getPhone();
            }

            this.estimateAmount = m.getEstimateAmount();
            this.estimateComment = m.getEstimateComment();
            this.workStartDate = m.getWorkStartDate();
            this.workEndDate = m.getWorkEndDate();

            this.resultComment = m.getResultComment();
            this.resultPhotoUrl = m.getResultPhotoUrl();
            this.workCompletedAt = m.getWorkCompletedAt();

            this.approvedByName = m.getApprovedBy() != null ? m.getApprovedBy().getName() : null;
            this.approvedAt = m.getApprovedAt();

            this.createdAt = m.getCreatedAt();
            this.submittedAt = m.getSubmittedAt();
            this.vendorSubmittedAt = m.getVendorSubmittedAt();
            this.rejectedReason = m.getRejectedReason();
        }
    }
}
