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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException; // ✅ 추가

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

    // ✅ 정적 리소스 404 (봇들이 wp-admin, index.php 등 찌르는 것) -> 조용히 404로 종료

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> exNoResourceFound(NoResourceFoundException e) {
        // 봇 스캔(워드프레스 등) 때문에 로그가 지저분해지는 걸 방지
        return Resp.fail(HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    // ✅ Spring MVC 핸들러 404 (컨트롤러 매핑 자체가 없음)
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<?> exNoHandlerFound(NoHandlerFoundException e) {
        return Resp.fail(HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> exMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Resp.fail(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> exMediaType(HttpMediaTypeNotSupportedException e) {
        return Resp.fail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
    }

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
        log.error("알 수 없는 에러 발생", e);
        return Resp.fail(HttpStatus.INTERNAL_SERVER_ERROR, "관리자에게 문의하세요");
    }

}
