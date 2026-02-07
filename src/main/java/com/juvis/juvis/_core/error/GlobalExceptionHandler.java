package com.juvis.juvis._core.error;

import com.juvis.juvis._core.error.ex.*;
import com.juvis.juvis._core.util.Resp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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
        log.error("API 500: {}", e.getMessage());
        return Resp.fail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    // ✅ Spring 기본 404는 404로 유지 (Actuator/정적리소스 등 포함)
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<?> exNoHandlerFound(NoHandlerFoundException e) {
        return Resp.fail(HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    // ✅ 잘못된 HTTP Method (GET/POST 혼동 등)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> exMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Resp.fail(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED");
    }

    // ✅ Content-Type 문제
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> exMediaType(HttpMediaTypeNotSupportedException e) {
        return Resp.fail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
    }

    // ✅ 파라미터 누락/타입 오류 등은 400 유지
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<?> exBadRequest(Exception e) {
        return Resp.fail(HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> exUnKnown(Exception e) {
        // ✅ 진짜 서버 에러만 여기로 오게
        log.error("알 수 없는 에러 발생", e);
        return Resp.fail(HttpStatus.INTERNAL_SERVER_ERROR, "관리자에게 문의하세요");
    }
}
