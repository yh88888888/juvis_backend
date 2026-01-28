package com.juvis.juvis.maintenance_vendor.maintenance_photo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

public class Presign {

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

    // ✅ GET 요청 DTO
    @Getter @Setter
    public static class PresignGetRequest {
        private String fileKey;
    }

    // ✅ GET 응답 DTO
    @Getter
    @AllArgsConstructor
    public static class PresignGetResponse {
        private String viewUrl;
        private String fileKey;
    }
    
}
