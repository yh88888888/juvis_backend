package com.juvis.juvis.user.authToken;

import org.springframework.stereotype.Service;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.juvis.juvis._core.error.ex.ExceptionApi401;
import com.juvis.juvis._core.util.JwtUtil;
import com.juvis.juvis.user.User;
import com.juvis.juvis.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final UserRepository userRepository;


    public String refreshAccessToken(String refreshToken) {
        DecodedJWT decoded = JwtUtil.verifyRefreshToken(refreshToken);

        String username = decoded.getSubject(); // ✅ 여기

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ExceptionApi401("사용자 없음"));

        return JwtUtil.createAccessToken(user);
    }
}