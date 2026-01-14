package com.juvis.juvis.notification;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis._core.error.ex.ExceptionApi404;
import com.juvis.juvis.maintenance.Maintenance;
import com.juvis.juvis.maintenance.MaintenanceRepository;
import com.juvis.juvis.user.LoginUser;
import com.juvis.juvis.user.User;
import com.juvis.juvis.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MaintenanceRepository maintenanceRepository;

    // =========================
    // 1) 상태 변경 기반 알림
    // =========================
    @Transactional
    public void notifyOnStatusChange(Maintenance m, MaintenanceStatus before, MaintenanceStatus after) {
        if (before == after) return;

        Set<User> targets = new LinkedHashSet<>();

        // HQ: (any)->REQUESTED, ESTIMATING->APPROVAL_PENDING, IN_PROGRESS->COMPLETED
        if (after == MaintenanceStatus.REQUESTED
                || (before == MaintenanceStatus.ESTIMATING && after == MaintenanceStatus.APPROVAL_PENDING)
                || (before == MaintenanceStatus.IN_PROGRESS && after == MaintenanceStatus.COMPLETED)) {
            targets.addAll(userRepository.findByRole(UserRole.HQ));
        }

        // Vendor: REQUESTED->ESTIMATING, APPROVAL_PENDING->IN_PROGRESS
        if (m.getVendor() != null) {
            if ((before == MaintenanceStatus.REQUESTED && after == MaintenanceStatus.ESTIMATING)
                    || (before == MaintenanceStatus.APPROVAL_PENDING && after == MaintenanceStatus.IN_PROGRESS)) {
                targets.add(m.getVendor());
            }
        }

        // Branch(요청자): ESTIMATING->APPROVAL_PENDING, APPROVAL_PENDING->IN_PROGRESS(✅ 추가)
        if (m.getRequester() != null) {
            if ((before == MaintenanceStatus.ESTIMATING && after == MaintenanceStatus.APPROVAL_PENDING)
                    || (before == MaintenanceStatus.APPROVAL_PENDING && after == MaintenanceStatus.IN_PROGRESS)) {
                targets.add(m.getRequester());
            }
        }

        // 저장 + dedupe
        for (User u : targets) {
            boolean exists = notificationRepository.existsByUserAndMaintenanceAndStatusAndEventType(
                    u, m, after, NotificationEventType.STATUS_CHANGED);
            if (exists) continue;

            try {
                notificationRepository.save(Notification.statusChanged(u, m, after));
            } catch (Exception ignore) {}
        }
    }

    // =========================
    // 2) “견적 수정” 이벤트 알림 (상태 유지)
    // - Branch(요청자) + HQ 전체에게 알림
    // =========================
    @Transactional
    public void notifyEstimateUpdated(Maintenance m) {
        if (m.getStatus() != MaintenanceStatus.APPROVAL_PENDING) return;

        Set<User> targets = new LinkedHashSet<>();

        // Branch(요청자)
        if (m.getRequester() != null) {
            targets.add(m.getRequester());
        }

        // HQ 전체
        targets.addAll(userRepository.findByRole(UserRole.HQ));

        if (targets.isEmpty()) return;

        // ✅ 매 수정마다 중복키가 달라지게(초 단위)
        // (Notification 엔티티 unique key가 user_id, maintenance_id, event_type, attempt_no 이므로)
        int dedupeKey = (int) java.time.Instant.now().getEpochSecond();

        for (User u : targets) {
            try {
                notificationRepository.save(Notification.estimateUpdated(u, m, dedupeKey));
            } catch (Exception ignore) {}
        }
    }

    // 예: 상태 변경 엔드포인트에서 사용
    @Transactional
    public void changeStatus(Long maintenanceId, MaintenanceStatus next) {
        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ExceptionApi404("유지보수 없음"));

        MaintenanceStatus before = m.getStatus();
        m.changeStatus(next);
        notifyOnStatusChange(m, before, next);
    }

    // 예: “견적 수정” API(벤더 PUT)에서 호출
    @Transactional
    public void onVendorEstimateUpdated(Long maintenanceId) {
        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ExceptionApi404("유지보수 없음"));
        notifyEstimateUpdated(m);
    }

    // =========================
    // 나머지 기존 기능
    // =========================
    public java.util.List<NotificationResponse.ItemDTO> list(LoginUser loginUser) {
        User me = loadUser(loginUser);
        return notificationRepository.findTop50ByUserOrderByCreatedAtDesc(me)
                .stream().map(NotificationResponse.ItemDTO::new).toList();
    }

    public long unreadCount(LoginUser loginUser) {
        User me = loadUser(loginUser);
        return notificationRepository.countByUserAndIsReadFalse(me);
    }

    @Transactional
    public void markRead(LoginUser loginUser, Long id) {
        User me = loadUser(loginUser);
        Notification n = notificationRepository.findByIdAndUser(id, me)
                .orElseThrow(() -> new ExceptionApi404("알림이 없습니다."));
        n.markRead();
    }

    @Transactional
    public void markAllRead(LoginUser user) {
        notificationRepository.markAllReadByUserId(user.id().longValue());
    }

    private User loadUser(LoginUser loginUser) {
        if (loginUser == null) throw new ExceptionApi403("로그인이 필요합니다.");
        return userRepository.findById(loginUser.id())
                .orElseThrow(() -> new ExceptionApi404("사용자 없음"));
    }
}
