package com.juvis.juvis._core.error;

import com.juvis.juvis._core.util.RespFilterUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.io.PrintWriter;

public class Jwt403Handler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        PrintWriter out = response.getWriter(); // 응답 버퍼에 직접 작성
        String responseBody = RespFilterUtil.fail(HttpServletResponse.SC_FORBIDDEN, accessDeniedException.getMessage());
        out.println(responseBody);
        out.flush();
    }
}