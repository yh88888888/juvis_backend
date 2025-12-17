package com.juvis.juvis.maintenance_photo;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.juvis.juvis.storage.StorageProps;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class PresignService {

    private final S3Presigner presigner;
    private final StorageProps props;

    public UploadController.PresignResponse presignPut(UploadController.PresignRequest req) {
        String ext = safeExt(req.getFileName());
        String key = props.getPrefix() + "/" + LocalDate.now() + "/" + UUID.randomUUID() + "." + ext;

        PutObjectRequest por = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .contentType(req.getContentType())
                .build();

        PutObjectPresignRequest presignReq = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(por)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignReq);

        return new UploadController.PresignResponse(presigned.url().toString(), key);
    }

    private String safeExt(String fileName) {
        if (fileName == null) return "jpg";
        String lower = fileName.toLowerCase();

        if (lower.endsWith(".png")) return "png";
        if (lower.endsWith(".webp")) return "webp";
        return "jpg";
    }
}
