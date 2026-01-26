package com.juvis.juvis.notification;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juvis.juvis._core.util.Resp;
import com.juvis.juvis.user.LoginUser;
import com.juvis.juvis.user.UserRepository;
import com.juvis.juvis.user_device.UserDevice;
import com.juvis.juvis.user_device.UserDeviceRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/test")
public class PushTestController {

    private final NotificationService notificationService;
    private final FcmPushService fcmPushService;
    private final UserDeviceRepository userDeviceRepository;

    @PostMapping("/push-me")
    public ResponseEntity<Resp<Void>> pushMe(@AuthenticationPrincipal LoginUser loginUser) {
        // 로그인한 내 id로 DB에서 토큰 조회
        List<String> tokens = userDeviceRepository.findActiveTokensByUserIds(List.of(loginUser.id()));
        if (tokens.isEmpty())
            return Resp.ok(null);

        String token = tokens.get(0); // 가장 최신 1개만
        fcmPushService.sendToTokens(
                List.of(token),
                "🔥 테스트",
                "지금 이 알림이 뜨면 성공",
                Map.of("type", "TEST"));
        return Resp.ok(null);
    }

    @PostMapping("/push")
    public void pushTest(@RequestHeader(value = "Authorization", required = false) String auth) {
        System.out.println("AUTH HEADER = [" + auth + "]");
        notificationService.sendTestPush(42L);
    }
}