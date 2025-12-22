package com.juvis.juvis.maintenance_admin;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.juvis.juvis.user.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hq/maintenances")
public class MaintenanceAdminController {

    private final MaintenanceAdminService maintenanceAdminService;

    // ✅ 요약
    @GetMapping("/summary")
    public MaintenanceAdminResponse.SummaryDTO summary(
            @AuthenticationPrincipal LoginUser loginUser
    ) {       
        return maintenanceAdminService.getSummary(loginUser);
    }

    // ✅ 목록 (status=REQUESTED 등)
    @GetMapping
    public MaintenanceAdminResponse.ListDTO list(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(value = "status", required = false) String status
    ) {
            return maintenanceAdminService.getList(loginUser, status);
    }
}