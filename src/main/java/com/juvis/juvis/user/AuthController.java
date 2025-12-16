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

    /**
     * HQ만 호출 가능한 "지점 + 지점계정 동시 생성" API
     * 해운대점 / juvis_hw / 000-0000-0000 / 부산 해운대구 ... 이런 식으로 사용.
     */
    @PostMapping("/api/auth/branch-join")
    public ResponseEntity<?> joinBranch(
            @AuthenticationPrincipal User loginUser,
            @Valid @RequestBody UserRequest.BranchJoinDTO reqDTO) {
        var respDTO = userService.joinBranch(reqDTO, loginUser);
        return Resp.ok(respDTO);
    }

    @GetMapping("/api/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        if (user == null)
            throw new ExceptionApi401("인증 필요");

        UserResponse.MeDTO dto = new UserResponse.MeDTO(
                user.getId(), // 네 User id 타입에 맞게 조정
                user.getUsername(),
                user.getName(), // name 필드 없다면 제거
                user.getRole().name());

        return Resp.ok(dto);
    }
}
