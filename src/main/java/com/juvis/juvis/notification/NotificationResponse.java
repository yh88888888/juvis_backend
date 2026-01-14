package com.juvis.juvis.notification;

import java.time.LocalDateTime;
import com.juvis.juvis._core.enums.MaintenanceStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class NotificationResponse {

    @Getter
    @AllArgsConstructor
    public static class ItemDTO {
        private Long id;
        private Long maintenanceId;
        private String title;
        private MaintenanceStatus status;
        private NotificationEventType eventType; // ✅ 추가
        private String message;
        private boolean read;
        private LocalDateTime createdAt;

        public ItemDTO(Notification n) {
            this.id = n.getId();
            this.maintenanceId = n.getMaintenance().getId();
            this.title = n.getMaintenance().getTitle();
            this.status = n.getStatus();
            this.eventType = n.getEventType(); // ✅ 추가
            this.message = n.getMessage();
            this.read = n.isRead();
            this.createdAt = n.getCreatedAt();
        }
    }
}