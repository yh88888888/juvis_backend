package com.juvis.juvis.maintenance_photo;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juvis.juvis._core.util.Resp;
import com.juvis.juvis.user.LoginUser;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

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
        maintenancePhotoService.attach(
                maintenanceId,
                loginUser.id(),
                dto
        );
        return Resp.ok(null);
    }
}

   