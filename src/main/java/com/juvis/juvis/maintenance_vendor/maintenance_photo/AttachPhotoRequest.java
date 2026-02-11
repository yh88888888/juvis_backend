package com.juvis.juvis.maintenance_vendor.maintenance_photo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttachPhotoRequest {

    // ✅ S3 object key만 저장 (presigned URL 저장 X)
    private String fileKey;

    // ✅ "REQUEST" | "ESTIMATE" | "RESULT"
    private String photoType;

    // ✅ ESTIMATE일 때만 필요 (1 or 2)
    private Integer attemptNo;
}
