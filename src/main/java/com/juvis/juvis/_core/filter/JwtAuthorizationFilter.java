package com.juvis.juvis._core.filter;

import com.juvis.juvis._core.util.JwtUtil;
import com.juvis.juvis.user.LoginUser;
import com.juvis.juvis.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.IOException;
import java.util.List;

// 단 한번만 실행되는 필터
public class JwtAuthorizationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(JwtUtil.HEADER);

        // 토큰이 없으면 그냥 통과 (보안은 SecurityConfig에서 URL별로 결정)
        if (authHeader == null || !authHeader.startsWith(JwtUtil.TOKEN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            User user = JwtUtil.verifyAndExtractUser(authHeader);

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

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
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}