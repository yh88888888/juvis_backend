package com.juvis.juvis.maintenance_photo;

import com.juvis.juvis.maintenance.Maintenance;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "maintenance_photo")
public class MaintenancePhoto {

    public enum PhotoType {
        REQUEST, // 지점 요청 첨부
        RESULT   // 벤더 완료 사진
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_id", nullable = false)
    private Maintenance maintenance;

    @Column(nullable = false)
    private String fileKey;

    @Column(nullable = false)
    private String publicUrl;

    // ✅ 추가: 사진 용도 구분
    @Enumerated(EnumType.STRING)
    @Column(name = "photo_type", nullable = false, length = 20)
    private PhotoType photoType;

    public static MaintenancePhoto of(
            Maintenance maintenance,
            String fileKey,
            String publicUrl,
            PhotoType photoType
    ) {
        return MaintenancePhoto.builder()
                .maintenance(maintenance)
                .fileKey(fileKey)
                .publicUrl(publicUrl)
                .photoType(photoType)
                .build();
    }
}