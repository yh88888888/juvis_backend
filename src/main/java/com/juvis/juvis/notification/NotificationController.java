package com.juvis.juvis.notification;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juvis.juvis._core.util.Resp;
import com.juvis.juvis.user.LoginUser;

import lombok.RequiredArgsConstructor;

// 알림목록
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal LoginUser user) {
        return Resp.ok(notificationService.list(user));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(@AuthenticationPrincipal LoginUser user) {
        return Resp.ok(notificationService.unreadCount(user));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> read(@AuthenticationPrincipal LoginUser user,
            @PathVariable("id") Long id) {
        notificationService.markRead(user, id);
        return Resp.ok("OK");
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> readAll(@AuthenticationPrincipal LoginUser user) {
        notificationService.markAllRead(user);
        return Resp.ok("OK");
    }
}
