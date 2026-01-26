package com.juvis.juvis.user_device;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.juvis.juvis._core.util.Resp;
import com.juvis.juvis.user.LoginUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/device")
public class UserDeviceController {

    private final UserDeviceService userDeviceService;

    @PostMapping("/token")
    public ResponseEntity<Resp<Void>> upsertToken(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody DeviceTokenRequest req) {
        log.info("📲 DEVICE TOKEN upsert userId={}, platform={}, tokenHead={}",
                loginUser.id(), req.platform(), req.token().substring(0, 12));
        userDeviceService.upsert(loginUser, req);
        return Resp.ok(null);
    }
}