package com.juvis.juvis.maintenance_photo;

import lombok.Data;

@Data
public class AttachPhotoRequest {
    private String fileKey;
    private String url;
}
