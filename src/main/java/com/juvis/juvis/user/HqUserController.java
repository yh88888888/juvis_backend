package com.juvis.juvis.user;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juvis.juvis._core.util.Resp;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hq")
public class HqUserController {

    private final UserService userService;

    // ✅ 지점 사용자 전체 조회
    @GetMapping("/branch-users")
    public ResponseEntity<?> branchUsers(
            @AuthenticationPrincipal LoginUser loginUser) {

        return Resp.ok(userService.getBranchUsers(loginUser));
    }

    // ✅ 활성/비활성 토글
    @PatchMapping("/users/{id}/active")
public ResponseEntity<?> toggleActive(
        @PathVariable("id") Integer id,
        @AuthenticationPrincipal LoginUser loginUser,
        @RequestBody Map<String, Boolean> body) {

    Boolean active = body.get("active");
    userService.updateActive(id, active, loginUser);
    return Resp.ok(null);
}

    // ✅ 비밀번호 초기화 (HQ 전용)
    @PatchMapping("/users/{id}/password-reset")
    public ResponseEntity<?> resetPassword(
            @PathVariable("id") Integer id,
            @AuthenticationPrincipal LoginUser loginUser) {

        userService.resetPasswordToDefault(id, loginUser);
        return Resp.ok(null);
    }
}