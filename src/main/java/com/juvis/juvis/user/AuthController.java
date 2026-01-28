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
    public ResponseEntity<?> me(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null)
            throw new ExceptionApi401("인증 필요");

        UserResponse.MeDTO dto = new UserResponse.MeDTO(
                loginUser.id(),
                loginUser.username(),
                null, // name 필요하면 DB에서 로드
                loginUser.role().name(),
                false // mustChangePassword도 DB 로드 시 채움
        );

        return Resp.ok(dto);
    }
}