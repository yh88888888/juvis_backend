package com.juvis.juvis.vendor_worker;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.juvis.juvis._core.util.Resp;
import com.juvis.juvis.user.LoginUser;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vendor/me/workers")
public class VendorWorkerController {

    private final VendorWorkerService service;
    private final VendorAuthFacade auth;

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal LoginUser loginUser) {
        Long vendorId = auth.requireVendorId(loginUser);
        return Resp.ok(service.list(vendorId));
    }

    @PostMapping
    public ResponseEntity<?> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody VendorWorkerDtos.CreateReq req) {
        Long vendorId = auth.requireVendorId(loginUser);
        return Resp.ok(service.create(vendorId, req));
    }

    @DeleteMapping("/{workerId}")
    public ResponseEntity<?> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long workerId) {
        Long vendorId = auth.requireVendorId(loginUser);
        service.delete(vendorId, workerId);
        return Resp.ok(null);
    }
}