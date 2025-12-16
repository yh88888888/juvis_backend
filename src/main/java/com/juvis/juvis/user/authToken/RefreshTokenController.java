package com.juvis.juvis.user.authToken;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.juvis.juvis._core.util.Resp;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RefreshTokenController {

    private final RefreshTokenService refreshTokenService;

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {

        String newAccessToken = refreshTokenService.refreshAccessToken(request.getRefreshToken());

        return Resp.ok(new RefreshTokenResponse(newAccessToken));
    }
}
