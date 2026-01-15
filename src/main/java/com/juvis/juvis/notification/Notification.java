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
@AllArgsConstructor(access = AccessLevel.PRIVATE) // ✅ Builder용
@Builder // ✅ builder() 생성
@Table(name = "notification", uniqueConstraints = @UniqueConstraint(name = "uq_notif_dedupe", columnNames = { "user_id",
        "maintenance_id", "event_type", "status", "attempt_no" }), indexes = {
                @Index(name = "idx_notif_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_notif_user_read", columnList = "user_id, is_read")
        })
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maintenance_id", nullable = false)
    private Maintenance maintenance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private MaintenanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private NotificationEventType eventType;

    // ✅ dedupe 키
    // STATUS_CHANGED → 항상 0
    // ESTIMATE_UPDATED → attemptNo
    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "message", nullable = false, length = 255)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }

    // =====================================================
    // 상태 변경 알림
    // =====================================================
    public static Notification statusChanged(
            User user,
            Maintenance m,
            MaintenanceStatus status) {

        String msg;

        switch (user.getRole()) {
            case VENDOR -> {
                if (status == MaintenanceStatus.ESTIMATING) {
                    msg = "새 견적 요청이 도착했습니다. 견적을 제출해주세요.";
                } else if (status == MaintenanceStatus.IN_PROGRESS) {
                    msg = "견적이 승인되었습니다. 작업을 진행해주세요.";
                } else {
                    msg = "요청 상태가 변경되었습니다.";
                }
            }

            case HQ -> {
                if (status == MaintenanceStatus.REQUESTED) {
                    msg = "지점에서 유지보수 요청이 제출되었습니다. 검토가 필요합니다.";
                } else if (status == MaintenanceStatus.APPROVAL_PENDING) {
                    msg = "업체에서 견적을 제출했습니다. 승인 또는 반려를 진행해주세요.";
                } else if (status == MaintenanceStatus.COMPLETED) {
                    msg = "작업이 완료되었습니다. 결과를 확인해주세요.";
                } else {
                    msg = "요청 상태가 변경되었습니다.";
                }
            }

            case BRANCH -> {
                if (status == MaintenanceStatus.APPROVAL_PENDING) {
                    msg = "업체 견적이 제출되었습니다. 본사 승인 대기 중입니다.";
                } else if (status == MaintenanceStatus.IN_PROGRESS) {
                    msg = "작업 일정이 확정되었습니다. 업체 연락처를 확인해주세요.";
                } else {
                    msg = "요청 상태가 변경되었습니다.";
                }
            }

            default -> msg = "요청 상태가 변경되었습니다.";
        }

        return Notification.builder()
                .user(user)
                .maintenance(m)
                .status(status)
                .eventType(NotificationEventType.STATUS_CHANGED)
                .attemptNo(0) // ✅ STATUS_CHANGED는 항상 0
                .message(msg)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // =====================================================
    // 견적 수정 알림 (중복 허용)
    // =====================================================
    public static Notification estimateUpdated(
            User user,
            Maintenance m,
            int attemptNo) {
        return Notification.builder()
                .user(user)
                .maintenance(m)
                .status(m.getStatus())
                .eventType(NotificationEventType.ESTIMATE_UPDATED)
                .attemptNo(attemptNo) // ✅ 매번 다른 값 → dedupe 통과
                .message("'" + m.getTitle() + "' 견적이 수정되었습니다.")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public void markRead() {
        this.isRead = true;
    }
}
