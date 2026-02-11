package com.juvis.juvis.maintenance_vendor;

import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis.maintenance.Maintenance;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

public class MaintenanceVendorResponse {

    @Getter
    @AllArgsConstructor
    public static class SummaryDTO {
        private long estimating;       // 견적 제출 필요
        private long hq2Rejected;       // 본사 견적 반려려
        private long approvalPending;  // 견적 제출(승인 대기)
        private long inProgress;       // 작업 중(결과 제출 필요)
        private long completed;        // 작업 완료
    }

    @Getter
    @AllArgsConstructor
    public static class ListItemDTO {
        private Long id;
        private String title;
        private String description; 
        private MaintenanceStatus status;
        private String branchName;
        private String requesterName;
        private LocalDateTime createdAt;

        public ListItemDTO(Maintenance m) {
            this.id = m.getId();
            this.title = m.getTitle();
            this.description = m.getDescription(); 
            this.status = m.getStatus();
            this.branchName = m.getBranch() != null ? m.getBranch().getBranchName() : null;
            this.requesterName = m.getRequester() != null ? m.getRequester().getName() : null;
            this.createdAt = m.getCreatedAt();
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ListDTO {
        private List<ListItemDTO> items;
    }
}