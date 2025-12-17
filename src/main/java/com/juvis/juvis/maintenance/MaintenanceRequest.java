package com.juvis.juvis.maintenance;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.juvis.juvis._core.enums.MaintenanceCategory;

public class MaintenanceRequest {

    @Getter
    @Setter
    @Data
    public static class CreateDTO {
        private String title;
        private String description;
        private MaintenanceCategory category;
        private boolean submit;

        // 👇 이걸로 간다
        private List<PhotoDTO> photos;
    }

    @Data
    public static class PhotoDTO {
        private String fileKey;
        private String url;
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
