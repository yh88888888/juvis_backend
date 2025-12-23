package com.juvis.juvis.notification;

import java.time.LocalDateTime;

import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis.maintenance.Maintenance; // (네가 실제로 쓰는 엔티티 클래스)
import com.juvis.juvis.user.User;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification", uniqueConstraints = @UniqueConstraint(name = "uq_notif_dedupe", columnNames = { "user_id",
    "maintenance_id", "status", "is_read" }), indexes = {
        @Index(name = "idx_notif_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_notif_user_read", columnList = "user_id, is_read")
    })
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id") // 명시(안 해도 되지만 안전)
  private Long id;

  // ✅ DDL: user_id
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
  private User user;

  // ✅ DDL: maintenance_id (maintenance_request.request_id를 참조한다면 Maintenance 엔티티의
  // PK 컬럼명도 확인 필요)
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "maintenance_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
  private Maintenance maintenance;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 50)
  private MaintenanceStatus status;

  @Column(name = "message", nullable = false, length = 255)
  private String message;

  @Column(name = "is_read", nullable = false)
  private boolean isRead = false;

  // ✅ DDL: created_at (DB 기본값 쓰고 싶으면 insertable=false로 바꿀 수도 있음)
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Notification(User user, Maintenance m) {
    this.user = user;
    this.maintenance = m;
    this.status = m.getStatus();
    this.message = m.getTitle() + "'이 " + m.getStatus().kr() + " 입니다.";
  }

  public void markRead() {
    this.isRead = true;
  }
}
