package com.juvis.juvis.notification;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
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

    // ✅ 상태별 수신 정책 (원하면 여기만 바꾸면 됨)
    private boolean allowBranch(MaintenanceStatus s) {
        return s != MaintenanceStatus.DRAFT && s != MaintenanceStatus.REQUESTED;
    }

    private boolean allowHq(MaintenanceStatus s) {
        return s != MaintenanceStatus.DRAFT;
    }

    private boolean allowVendor(MaintenanceStatus s) {
        return s != MaintenanceStatus.DRAFT;
    }

    @Transactional
    public void notifyOnStatusChange(
            Maintenance m,
            MaintenanceStatus before,
            MaintenanceStatus after) {

        // ✅ 실제 상태 변경 없으면 종료
        if (before == after)
            return;

        Set<User> targets = new LinkedHashSet<>();

        // =========================
        // 1️⃣ 본사(HQ) — 항상
        // =========================
        targets.addAll(userRepository.findByRole(UserRole.HQ));

        // =========================
        // 2️⃣ 지점(요청자)
        // =========================
        if (m.getRequester() != null &&
                EnumSet.of(
                        MaintenanceStatus.APPROVAL_PENDING,
                        MaintenanceStatus.IN_PROGRESS,
                        MaintenanceStatus.COMPLETED,
                        MaintenanceStatus.HQ1_REJECTED).contains(after)) {
            targets.add(m.getRequester());
        }

        // =========================
        // 3️⃣ 업체(VENDOR)
        // =========================
        if (m.getVendor() != null &&
                EnumSet.of(
                        MaintenanceStatus.ESTIMATING,
                        MaintenanceStatus.IN_PROGRESS,
                        MaintenanceStatus.HQ2_REJECTED).contains(after)) {
            targets.add(m.getVendor());
        }

        // =========================
        // 4️⃣ 알림 저장 (중복 방지)
        // =========================
        for (User u : targets) {
            boolean exists = notificationRepository
                    .existsByUserAndMaintenanceAndStatusAndIsReadFalse(
                            u, m, after);

            if (exists)
                continue;

            try {
                notificationRepository.save(
                        new Notification(u, m));
            } catch (Exception ignore) {
                // unique 충돌 등은 무시
            }
        }
    }

    @Transactional
    public void changeStatus(Long maintenanceId, MaintenanceStatus next) {

        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ExceptionApi404("유지보수 없음"));

        MaintenanceStatus before = m.getStatus();

        // ✅ 이제 정상 동작
        m.changeStatus(next);

        notifyOnStatusChange(m, before, next);
    }

    public List<NotificationResponse.ItemDTO> list(LoginUser loginUser) {
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

    private User loadUser(LoginUser loginUser) {
        if (loginUser == null)
            throw new ExceptionApi403("로그인이 필요합니다.");

        Long userId = loginUser.id().longValue(); // ✅ Integer → Long 변환

        return userRepository.findById(loginUser.id())
                .orElseThrow(() -> new ExceptionApi404("사용자 없음"));
    }

    @Transactional
    public void markAllRead(LoginUser user) {
        notificationRepository.markAllReadByUserId(user.id());
    }

}