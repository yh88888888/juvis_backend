package com.juvis.juvis.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis.maintenance.Maintenance;
import com.juvis.juvis.user.User;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  List<Notification> findTop50ByUserOrderByCreatedAtDesc(User user);

  long countByUserAndIsReadFalse(User user);

  Optional<Notification> findByIdAndUser(Long id, User user);

  boolean existsByUserAndMaintenanceAndStatusAndEventType(
      User user,
      Maintenance maintenance,
      MaintenanceStatus status,
      NotificationEventType eventType);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
          update Notification n
             set n.isRead = true
           where n.user.id = :userId
             and n.isRead = false
      """)
  int markAllReadByUserId(@Param("userId") Long userId);

  boolean existsByUserAndMaintenanceAndEventTypeAndCreatedAtAfter(
      User user,
      Maintenance maintenance,
      NotificationEventType eventType,
      java.time.LocalDateTime after);
}
