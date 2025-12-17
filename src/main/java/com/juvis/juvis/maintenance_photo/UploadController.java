package com.juvis.juvis.maintenance_photo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.juvis.juvis._core.util.Resp;

import lombok.*;

@RestController
@RequiredArgsConstructor
public class UploadController {

    private final PresignService presignService;

    @PostMapping("/api/uploads/presign")
    public ResponseEntity<?> presign(@RequestBody PresignRequest req) {
        return Resp.ok(presignService.presignPut(req));
    }

    @Getter @Setter
    public static class PresignRequest {
        private String fileName;
        private String contentType;
    }

    @Getter
    @AllArgsConstructor
    public static class PresignResponse {
        private String uploadUrl;
        private String fileKey;
    }
}
