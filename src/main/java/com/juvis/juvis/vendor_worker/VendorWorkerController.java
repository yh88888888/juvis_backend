package com.juvis.juvis.vendor_worker;



import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.juvis.juvis.user.LoginUser;

import java.util.List;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vendor/me/workers")
public class VendorWorkerController {

    private final VendorWorkerService service;
    private final VendorAuthFacade auth;

    @GetMapping
    public List<VendorWorkerDtos.ItemRes> list(@AuthenticationPrincipal LoginUser loginUser) {
        Long vendorId = auth.requireVendorId(loginUser);
        return service.list(vendorId);
    }

    @PostMapping
    public VendorWorkerDtos.ItemRes create(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody VendorWorkerDtos.CreateReq req
    ) {
        Long vendorId = auth.requireVendorId(loginUser);
        return service.create(vendorId, req);
    }

    @DeleteMapping("/{workerId}")
    public void delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long workerId
    ) {
        Long vendorId = auth.requireVendorId(loginUser);
        service.delete(vendorId, workerId);
    }
}