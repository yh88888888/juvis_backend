
package com.juvis.juvis.maintenance_vendor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.juvis.juvis._core.util.Resp;
import com.juvis.juvis.user.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vendor/maintenances")
public class MaintenanceVendorController {

    private final MaintenanceVendorService maintenanceVendorService;

    // ✅ Vendor 요약
    @GetMapping("/summary")
    public ResponseEntity<?> summary(@AuthenticationPrincipal LoginUser loginUser) {
        return Resp.ok(maintenanceVendorService.getSummary(loginUser));
    }

    // ✅ Vendor 목록 (status=ESTIMATING 등)
    @GetMapping
    public ResponseEntity<?> list(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(value = "status", required = false) String status) {
        System.out.println("VENDOR LIST status param = " + status);
        return Resp.ok(maintenanceVendorService.getList(loginUser, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id) {
        return Resp.ok(maintenanceVendorService.getDetail(loginUser, id));
    }
}
