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
        if (uri.startsWith("/actuator/"))
            return true;

        // ✅ 인증/문서
        if (uri.startsWith("/api/auth/"))
            return true;
        if (uri.startsWith("/docs/"))
            return true;

        // ✅ CORS preflight
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()))
            return true;

        if (uri.equals("/") || uri.equals("/index.html"))
            return true;
        if (uri.startsWith("/assets/"))
            return true;
        if (uri.startsWith("/canvaskit/"))
            return true;
        if (uri.startsWith("/icons/"))
            return true;
        if (uri.endsWith(".js") || uri.endsWith(".css") || uri.endsWith(".png") || uri.endsWith(".ico")
                || uri.endsWith(".json") || uri.endsWith(".svg"))
            return true;

        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(JwtUtil.HEADER);

        // 토큰 없으면 통과 (SecurityConfig에서 URL별로 제어)
        if (authHeader == null || !authHeader.startsWith(JwtUtil.TOKEN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            User user = JwtUtil.verifyAndExtractUser(authHeader);

            var authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

            var principal = new LoginUser(
                    user.getId(),
                    user.getUsername(),
                    user.getRole());

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            // (원인 추적 필요하면 잠깐 켜기)
            // log.warn("JWT auth failed uri={} msg={}", request.getRequestURI(),
            // e.getMessage());

            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
