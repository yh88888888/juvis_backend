package com.juvis.juvis.maintenance_vendor.maintenance_photo;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.juvis.juvis._core.util.Resp;
import com.juvis.juvis.user.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/maintenance")
public class MaintenancePhotoController {

    private final MaintenancePhotoService maintenancePhotoService;

    @PostMapping("/{maintenanceId}/photos")
    public ResponseEntity<?> attachPhoto(
            @PathVariable Long maintenanceId,
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody AttachPhotoRequest dto
    ) {
        maintenancePhotoService.attach(maintenanceId, loginUser.id(), dto);
        return Resp.ok(null);
    }
}
