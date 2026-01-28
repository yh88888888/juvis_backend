package com.juvis.juvis.maintenance_vendor.maintenance_photo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.juvis.juvis._core.util.Resp;
import com.juvis.juvis.maintenance_vendor.maintenance_photo.Presign.PresignRequest;

import lombok.*;

@RestController
@RequiredArgsConstructor
public class PresignController {

    private final PresignService presignService;

    @PostMapping("/api/uploads/presign")
    public ResponseEntity<?> presign(@RequestBody PresignRequest req) {
        return Resp.ok(presignService.presignPut(req));
    }



    
}
