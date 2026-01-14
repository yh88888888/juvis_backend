package com.juvis.juvis.notification;

import java.time.LocalDateTime;

import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis.maintenance.Maintenance;
import com.juvis.juvis.user.User;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "notification",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_notif_dedupe",
        columnNames = { "user_id", "maintenance_id", "event_type", "status", "attempt_no" }
    ),
    indexes = {
        @Index(name = "idx_notif_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_notif_user_read", columnList = "user_id, is_read")
    }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maintenance_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
    private Maintenance maintenance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private MaintenanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private NotificationEventType eventType;

    // ✅ dedupe용 키 (STATUS_CHANGED: 0 고정 / ESTIMATE_UPDATED: 매번 다른 값)
    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "message", nullable = false, length = 255)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // 상태 변경 알림
    public static Notification statusChanged(User user, Maintenance m, MaintenanceStatus after) {
        Notification n = new Notification();
        n.user = user;
        n.maintenance = m;
        n.status = after;
        n.eventType = NotificationEventType.STATUS_CHANGED;

        // ✅ 동일 상태 전환 알림은 중복 방지하고 싶으니 0 고정
        n.attemptNo = 0;

        n.message = "'" + m.getTitle() + "'이(가) " + after.kr() + " 상태로 변경되었습니다.";
        return n;
    }

    // 견적 수정 알림 (상태는 보통 APPROVAL_PENDING)
    public static Notification estimateUpdated(User user, Maintenance m, int attemptNo) {
        Notification n = new Notification();
        n.user = user;
        n.maintenance = m;
        n.status = m.getStatus();
        n.eventType = NotificationEventType.ESTIMATE_UPDATED;

        // ✅ 매 호출마다 다른 값(=중복 방지키)을 넣어줘서 "수정할 때마다 알림" 가능
        n.attemptNo = attemptNo;

        n.message = "'" + m.getTitle() + "' 견적이 수정되었습니다.";
        return n;
    }

    public void markRead() {
        this.isRead = true;
    }
}
