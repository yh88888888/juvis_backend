package com.juvis.juvis.maintenance;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
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
        private List<PhotoDTO> photos;
    }

    @Data
    public static class PhotoDTO {
        private String fileKey;

        @JsonAlias({ "publicUrl", "url" })
        private String url;

        public String getUrl() {
            if (url == null)
                return null;
            String t = url.trim();
            return t.isEmpty() ? null : t;
        }
    }

    @Getter
    @Setter
    public static class AssignVendorDTO {
        private Long vendorUserId;
    }

    @Getter
    @Setter
    public static class ApproveDTO {
        private String comment; // ✅ HQ1 승인 코멘트(선택)
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
        private BigDecimal finalAmount;
        private String estimateComment;
        private LocalDateTime workStartDate;
        private LocalDateTime workEndDate;

        // ✅ 추가
        private List<EstimatePhotoDTO> estimatePhotos;
        private Long workerId;

        @Getter
        @Setter
        public static class EstimatePhotoDTO {
            private String fileKey;
            private String publicUrl;
        }
    }

    @Getter
    @Setter
    public static class CompleteWorkDTO {
        private String resultComment; // 필수
        private LocalDateTime completedAt; // 선택 (원하면 안 써도 됨)
        private List<ResultPhotoDTO> resultPhotos;
        private BigDecimal finalAmount;

        @Getter
        @Setter
        public static class ResultPhotoDTO {
            private String fileKey;
            private String publicUrl;
        }
    }

    @Getter
    @Setter
    public static class UpdateEstimateDTO {
        private String estimateAmount;
        private String estimateComment;
        private LocalDateTime workStartDate;
        private LocalDateTime workEndDate;
        private Long workerId;

        // ✅ 사진 변경 여부: false면 서버는 사진 테이블을 절대 건드리지 않는다
        private Boolean photoChanged;

        // ✅ photoChanged=true일 때만 사용: 최종 사진 목록(기존 유지 + 새 사진)
        private List<SubmitEstimateDTO.EstimatePhotoDTO> estimatePhotos;
    }

    @Getter
    @Setter
    @Data
    public static class HqCreateDTO {
        private Long branchId; // ✅ 필수
        private String title;
        private String description;
        private MaintenanceCategory category;
        private boolean submit; // 저장/제출 동일 UX
        private List<PhotoDTO> photos;
    }
}
