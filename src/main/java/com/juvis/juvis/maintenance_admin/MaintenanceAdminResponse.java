package com.juvis.juvis.maintenance_admin;

import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis.maintenance.Maintenance;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

public class MaintenanceAdminResponse {

    @Getter
    @AllArgsConstructor
    public static class SummaryDTO {
        private long requested;
        private long estimating;
        private long approvalPending;
        private long hq2Rejected;
        private long inProgress;
        private long completed;
    }

    @Getter
    @AllArgsConstructor
    public static class ListItemDTO {
        private Long id;
        private String title;
        private MaintenanceStatus status;
        private String branchName;
        private String requesterName;
        private LocalDateTime createdAt;

        public ListItemDTO(Maintenance m) {
            this.id = m.getId();
            this.title = m.getTitle();
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
