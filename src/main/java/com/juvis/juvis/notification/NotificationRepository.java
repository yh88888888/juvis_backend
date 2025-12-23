package com.juvis.juvis.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis.maintenance.Maintenance;
import com.juvis.juvis.user.User;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUserOrderByCreatedAtDesc(User user);

    long countByUserAndIsReadFalse(User user);

    Optional<Notification> findByIdAndUser(Long id, User user);

    boolean existsByUserAndMaintenanceAndStatusAndIsReadFalse(
            User user, Maintenance maintenance, MaintenanceStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
                update Notification n
                   set n.isRead = true
                 where n.user.id = :userId
                   and n.isRead = false
            """)
    int markAllReadByUserId(@Param("userId") Integer userId);
}
