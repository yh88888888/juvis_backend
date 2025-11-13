package com.juvis.juvis._core.error;

import com.juvis.juvis._core.util.RespFilterUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.io.PrintWriter;

public class Jwt401Handler implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        PrintWriter out = response.getWriter(); // 응답 버퍼에 직접 작성
        String responseBody = RespFilterUtil.fail(HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage());
        out.println(responseBody);
        out.flush();
    }
}