// ============================
// JwtAuthorizationFilter.java
// ============================
package com.juvis.juvis._core.filter;

import com.juvis.juvis._core.util.JwtUtil;
import com.juvis.juvis.user.LoginUser;
import com.juvis.juvis.user.User;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthorizationFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();

        // ✅ Actuator는 JWT 필터 완전 제외
        if (uri.startsWith("/actuator/")) return true;

        // ✅ 인증/문서
        if (uri.startsWith("/api/auth/")) return true;
        if (uri.startsWith("/docs/")) return true;

        // ✅ CORS preflight
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(JwtUtil.HEADER);

        // 토큰 없으면 통과 (인가 여부는 SecurityConfig가 결정)
        if (authHeader == null || !authHeader.startsWith(JwtUtil.TOKEN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            User user = JwtUtil.verifyAndExtractUser(authHeader);

            var authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
            );

            var principal = new LoginUser(
                    user.getId(),
                    user.getUsername(),
                    user.getRole()
            );

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            // ✅ 여기서 body를 쓰지 말고 그냥 401만 주는게 깔끔
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
