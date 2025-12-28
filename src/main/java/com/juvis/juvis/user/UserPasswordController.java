package com.juvis.juvis.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juvis.juvis._core.error.ex.ExceptionApi401;
import com.juvis.juvis._core.util.Resp;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserPasswordController {

    private final UserService userService;

    public record ChangePasswordReq(String currentPassword, String newPassword) {}

    @PatchMapping("/me/password")
    public ResponseEntity<?> changeMyPassword(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody ChangePasswordReq body) {

        if (loginUser == null) throw new ExceptionApi401("인증 필요");

        if (body == null || body.newPassword() == null || body.newPassword().isBlank()) {
            return Resp.fail(HttpStatus.BAD_REQUEST, "새 비밀번호를 입력하세요.");
        }

        userService.changeMyPassword(loginUser.id(), body.currentPassword(), body.newPassword());
        return Resp.ok(null);
    }
}