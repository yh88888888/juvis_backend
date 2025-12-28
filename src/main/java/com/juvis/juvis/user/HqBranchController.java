package com.juvis.juvis.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.juvis.juvis._core.util.Resp;
import com.juvis.juvis._core.error.ex.ExceptionApi401;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hq")
public class HqBranchController {

    private final UserService userService;

    @PostMapping("/branches")
    public ResponseEntity<?> createBranch(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody UserRequest.BranchJoinDTO reqDTO
    ) {
        if (loginUser == null) {
            throw new ExceptionApi401("인증 필요");
        }

        var respDTO = userService.joinBranch(reqDTO, loginUser);
        return Resp.ok(respDTO);
    }
}