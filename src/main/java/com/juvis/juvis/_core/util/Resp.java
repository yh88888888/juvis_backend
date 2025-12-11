package com.juvis.juvis._core.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@AllArgsConstructor
@Data
public class Resp<T> {
    private Integer status;
    private String msg;
    private T body;

    public static <B> ResponseEntity<Resp<B>> ok(B body) {
        Resp<B> resp = new Resp<>(200, "성공", body);
        return new ResponseEntity<>(resp, HttpStatus.OK);
    }

    public static ResponseEntity<Resp<?>> fail(HttpStatus status, String msg) {
        Resp<?> resp = new Resp<>(status.value(), msg, null);
        return new ResponseEntity<>(resp, status);
    }

    public static Resp<?> fail(Integer status, String msg) {
        Resp<?> resp = new Resp<>(status, msg, null);
        return resp;
    }

    public static <B> ResponseEntity<Resp<B>> forbidden(String msg) {
        Resp<B> resp = new Resp<>(HttpStatus.FORBIDDEN.value(), msg, null);
        return new ResponseEntity<>(resp, HttpStatus.FORBIDDEN);
    }
}
