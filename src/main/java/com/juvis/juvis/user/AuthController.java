package com.juvis.juvis.user;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

import com.juvis.juvis._core.error.ex.ExceptionApi401;
import com.juvis.juvis._core.util.Resp;

import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserService userService;

    @PostMapping("/api/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody UserRequest.LoginDTO reqDTO, Errors errors) {
        var respDTO = userService.login(reqDTO);
        return Resp.ok(respDTO);
    }

    @GetMapping("/api/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        if (user == null)
            throw new ExceptionApi401("인증 필요");

        UserResponse.MeDTO dto = new UserResponse.MeDTO(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getRole().name(),
                user.isMustChangePassword() // ✅ 핵심

        );

        return Resp.ok(dto);
    }
}