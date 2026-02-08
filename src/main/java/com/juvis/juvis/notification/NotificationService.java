package com.juvis.juvis.notification;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import com.juvis.juvis.user_device.UserDeviceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final FcmPushService fcmPushService;

    // =========================
    // 1) 상태 변경 기반 알림
    // =========================
    @Transactional
    public void notifyOnStatusChange(Maintenance m, MaintenanceStatus before, MaintenanceStatus after) {
        if (before == after)
            return;

        Set<User> targets = new LinkedHashSet<>();

        // HQ: (any)->REQUESTED, ESTIMATING->APPROVAL_PENDING, IN_PROGRESS->COMPLETED
        if (after == MaintenanceStatus.REQUESTED
                || (before == MaintenanceStatus.ESTIMATING && after == MaintenanceStatus.APPROVAL_PENDING)
                || (before == MaintenanceStatus.IN_PROGRESS && after == MaintenanceStatus.COMPLETED)) {
            targets.addAll(userRepository.findByRole(UserRole.HQ));
        }

        // Vendor: REQUESTED->ESTIMATING, APPROVAL_PENDING->IN_PROGRESS
        if (m.getVendor() != null) {
            boolean vendorShouldNotify = (after == MaintenanceStatus.ESTIMATING) // ✅ DRAFT->ESTIMATING /
                                                                                 // REQUESTED->ESTIMATING 모두 포함
                    || (before == MaintenanceStatus.APPROVAL_PENDING && after == MaintenanceStatus.IN_PROGRESS);

            if (vendorShouldNotify) {
                targets.add(m.getVendor());
            }
        }

        // Branch(요청자): ESTIMATING->APPROVAL_PENDING, APPROVAL_PENDING->IN_PROGRESS(✅
        // 추가)
        if (m.getBranch() != null) {
            boolean branchShouldNotify = (before == MaintenanceStatus.REQUESTED
                    && after == MaintenanceStatus.ESTIMATING) // ✅ 추가
                    || (before == MaintenanceStatus.ESTIMATING
                            && after == MaintenanceStatus.APPROVAL_PENDING)
                    || (before == MaintenanceStatus.APPROVAL_PENDING && after == MaintenanceStatus.IN_PROGRESS);

            if (branchShouldNotify) {

                // 1️⃣ 요청자가 BRANCH면 요청자에게
                if (m.getRequester() != null && m.getRequester().getRole() == UserRole.BRANCH) {
                    targets.add(m.getRequester());

                    // 2️⃣ 요청자가 HQ 등 다른 역할이면 → 해당 지점의 BRANCH 1명에게
                } else if (m.getBranch() != null) {
                    targets.addAll(
                            userRepository.findAllByBranchIdAndRole(
                                    m.getBranch().getId(),
                                    UserRole.BRANCH));
                }
            }
        }

        // 저장 + dedupe
        for (User u : targets) {
            boolean exists = notificationRepository.existsByUserAndMaintenanceAndStatusAndEventType(
                    u, m, after, NotificationEventType.STATUS_CHANGED);
            if (exists)
                continue;

            try {
                notificationRepository.save(Notification.statusChanged(u, m, after));
            } catch (Exception ignore) {
            }
        }
        pushToTargets(targets, m, NotificationEventType.STATUS_CHANGED, after.name());
    }

    // =========================
    // 2) “견적 수정” 이벤트 알림 (상태 유지)
    // - Branch(요청자) + HQ 전체에게 알림
    // =========================
    @Transactional
    public void notifyEstimateUpdated(Maintenance m) {
        if (m.getStatus() != MaintenanceStatus.APPROVAL_PENDING)
            return;

        Set<User> targets = new LinkedHashSet<>();

        // Branch(요청자)
        if (m.getBranch() != null) {
            targets.addAll(userRepository.findAllByBranchIdAndRole(m.getBranch().getId(), UserRole.BRANCH));
        }

        // HQ 전체
        targets.addAll(userRepository.findByRole(UserRole.HQ));

        if (targets.isEmpty())
            return;

        // ✅ 매 수정마다 중복키가 달라지게(초 단위)
        // (Notification 엔티티 unique key가 user_id, maintenance_id, event_type, attempt_no
        // 이므로)
        int dedupeKey = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);

        for (User u : targets) {
            try {
                notificationRepository.save(Notification.estimateUpdated(u, m, dedupeKey));
            } catch (Exception ignore) {
            }
        }
        pushToTargets(targets, m, NotificationEventType.ESTIMATE_UPDATED, m.getStatus().name());
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
        if (loginUser == null)
            throw new ExceptionApi403("로그인이 필요합니다.");
        return userRepository.findById(loginUser.id())
                .orElseThrow(() -> new ExceptionApi404("사용자 없음"));
    }

    private void pushToTargets(Set<User> targets, Maintenance m, NotificationEventType eventType, String status) {
        if (targets == null || targets.isEmpty()) {
            log.info("📭 push skip: targets empty event={} mId={}", eventType, m.getId());
            return;
        }

        List<Integer> userIds = targets.stream().map(User::getId).toList();
        List<String> tokens = userDeviceRepository.findActiveTokensByUserIds(userIds);

        log.info("📨 push prepare: event={} mId={} targets={} tokens={}",
                eventType, m.getId(), userIds.size(), tokens.size());
        if (tokens.isEmpty())
            return;

        String title = "[유지보수] " + (m.getTitle() == null ? "" : m.getTitle());
        String body = (eventType == NotificationEventType.ESTIMATE_UPDATED)
                ? "견적이 수정되었습니다. 확인해주세요."
                : "요청 상태가 변경되었습니다: " + status;

        Map<String, String> data = Map.of(
                "type", eventType.name(),
                "maintenanceId", String.valueOf(m.getId()),
                "status", status);

        fcmPushService.sendToTokens(tokens, title, body, data);
    }

    @Transactional
    public void sendTestPush(Long userId) {

        // ⚠️ 지금은 DB에서 가져오는 대신, 방금 확인한 토큰을 하드코딩
        fcmPushService.sendToTokens(
                List.of("en5-fs2ETp6i8BzR9YEH3F..."),
                "🔥 테스트",
                "지금 이 알림이 뜨면 성공",
                Map.of("type", "TEST"));
    }
}
