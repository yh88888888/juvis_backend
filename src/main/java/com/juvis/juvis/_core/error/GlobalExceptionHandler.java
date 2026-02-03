package com.juvis.juvis._core.error;

import com.juvis.juvis._core.error.ex.*;
import com.juvis.juvis._core.util.Resp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // =========================
    // 커스텀 API 예외
    // =========================
    @ExceptionHandler(ExceptionApi400.class)
    public ResponseEntity<?> exApi400(ExceptionApi400 e) {
        log.warn(e.getMessage());
        return Resp.fail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ExceptionApi401.class)
    public ResponseEntity<?> exApi401(ExceptionApi401 e) {
        log.warn(e.getMessage());
        return Resp.fail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(ExceptionApi403.class)
    public ResponseEntity<?> exApi403(ExceptionApi403 e) {
        log.warn(e.getMessage());
        return Resp.fail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(ExceptionApi404.class)
    public ResponseEntity<?> exApi404(ExceptionApi404 e) {
        log.warn(e.getMessage());
        return Resp.fail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ExceptionApi500.class)
    public ResponseEntity<?> exApi500(ExceptionApi500 e) {
        log.error("알 수 없는 에러 발생: {}", e.getMessage());
        return Resp.fail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    // =========================
    // ✅ 404를 500으로 바꾸지 않도록 별도 처리 (중요)
    // =========================
    @ExceptionHandler({ NoHandlerFoundException.class, NoResourceFoundException.class })
    public ResponseEntity<?> exNotFound(Exception e) {
        log.warn("404 Not Found: {}", e.getMessage());
        return Resp.fail(HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    // =========================
    // ✅ 상태코드가 있는 예외는 상태코드 유지 (중요)
    // =========================
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> exResponseStatus(ResponseStatusException e) {
        HttpStatusCode code = e.getStatusCode();
        String msg = (e.getReason() != null) ? e.getReason() : "ERROR";
        log.warn("ResponseStatusException: {} {}", code.value(), msg);
        return Resp.fail(HttpStatus.valueOf(code.value()), msg);
    }

    // =========================
    // 그 외 전부 500
    // =========================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> exUnKnown(Exception e) {
        log.error("알 수 없는 에러 발생", e);
        return Resp.fail(HttpStatus.INTERNAL_SERVER_ERROR, "관리자에게 문의하세요");
    }
}
