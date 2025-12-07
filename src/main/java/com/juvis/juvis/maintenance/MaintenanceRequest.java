package com.juvis.juvis.maintenance;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.juvis.juvis._core.enums.MaintenanceCategory;

public class MaintenanceRequest {

    @Getter
    @Setter
    public static class CreateDTO {
        private String title;
        private String description;
        private MaintenanceCategory category;
        private boolean submit; // true면 바로 REQUESTED, false면 DRAFT

    }

    @Getter
    @Setter
    public static class AssignVendorDTO {
        private Long vendorUserId;
    }

    @Getter
    @Setter
    public static class ApproveDTO {
        // 필요시 필드 추가
    }

    @Getter
    @Setter
    public static class RejectDTO {
        private String reason;
    }

    @Getter
    @Setter
    public static class SubmitEstimateDTO {
        private BigDecimal estimateAmount;
        private String estimateComment;
        private LocalDate workStartDate;
        private LocalDate workEndDate;
    }

    @Getter
    @Setter
    public static class CompleteWorkDTO {
        private String resultComment;
        private String resultPhotoUrl; // S3 URL 등
        private LocalDate actualEndDate;
    }
}
