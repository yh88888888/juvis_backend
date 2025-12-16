package com.juvis.juvis._core.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi401;
import com.juvis.juvis.user.User;

import java.util.Date;

// JWT 토큰 생성 및 검증 유틸리티
public class JwtUtil {

    public static final String HEADER = "Authorization"; // HTTP 헤더 이름
    public static final String TOKEN_PREFIX = "Bearer "; // 토큰 접두사
    public static final String SECRET = "juvijuvijuvis"; // 토큰 서명에 사용될 비밀 키
    // Access Token: 2시간
    private static final long ACCESS_TOKEN_EXPIRE_TIME = 1000L * 60 * 60 * 2;
    // Refresh Token: 60일
    private static final long REFRESH_TOKEN_EXPIRE_TIME = 1000L * 60 * 60 * 24 * 60;

    // JWT access토큰 생성
    public static String createAccessToken(User user) {
        String jwt = JWT.create()
                .withSubject(user.getUsername()) // 토큰의 주체
                .withExpiresAt(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRE_TIME)) // 토큰 만료 시간
                .withClaim("id", user.getId()) // 사용자 ID 클레임 추가
                .withClaim("role", user.getRole().name()) // 사용자 역할 클레임 추가
                .sign(Algorithm.HMAC512(SECRET)); // 비밀 키로 서명

        return TOKEN_PREFIX + jwt; // "Bearer " 접두사 붙여 반환
    }

    // JWT refresh토큰 생성
    public static String createRefreshToken(User user) {
        return JWT.create()
                .withSubject(user.getUsername())
                .withExpiresAt(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRE_TIME))
                .withClaim("id", user.getId())
                .sign(Algorithm.HMAC512(SECRET));
    }


    public static DecodedJWT verifyAccessToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(TOKEN_PREFIX)) {
            throw new ExceptionApi401("유효하지 않은 Authorization 헤더");
        }

        String token = authorizationHeader.replace(TOKEN_PREFIX, "");

        return JWT.require(Algorithm.HMAC512(SECRET))
                .build()
                .verify(token);
    }

    public static DecodedJWT verifyRefreshToken(String refreshToken) {
        return JWT.require(Algorithm.HMAC512(SECRET))
                .build()
                .verify(refreshToken);
    }

    public static User verifyAndExtractUser(String authorizationHeader) {
        DecodedJWT decodedJWT = verifyAccessToken(authorizationHeader);

        Integer id = decodedJWT.getClaim("id").asInt();
        String username = decodedJWT.getSubject();
        UserRole role = UserRole.valueOf(decodedJWT.getClaim("role").asString());

        return User.builder()
                .id(id)
                .username(username)
                .role(role)
                .build();
    }
}
