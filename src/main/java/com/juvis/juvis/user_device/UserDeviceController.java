package com.juvis.juvis.user_device;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.juvis.juvis._core.util.Resp;
import com.juvis.juvis.user.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/device")
public class UserDeviceController {

    private final UserDeviceService userDeviceService;

    @PostMapping("/api/device/token")
    public ResponseEntity<Resp<Void>> upsertToken(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody DeviceTokenRequest req) {
        userDeviceService.upsert(loginUser, req);
        return Resp.ok(null);
    }
}