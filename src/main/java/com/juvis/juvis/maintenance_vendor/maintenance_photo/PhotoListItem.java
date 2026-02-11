package com.juvis.juvis.maintenance_vendor.maintenance_photo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PhotoListItem {
    private Long id;
    private String fileKey;
    private String viewUrl;     // ✅ GET presign
    private String photoType;
    private Integer attemptNo;
}