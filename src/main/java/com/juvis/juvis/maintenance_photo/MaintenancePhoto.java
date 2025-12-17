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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_id", nullable = false)
    private Maintenance maintenance;

    @Column(nullable = false)
    private String fileKey;

    @Column(nullable = false)
    private String url;

    // ✅ 이게 없어서 계속 에러 난 거다
    public static MaintenancePhoto of(
            Maintenance maintenance,
            String fileKey,
            String url) {
        return MaintenancePhoto.builder()
                .maintenance(maintenance)
                .fileKey(fileKey)
                .url(url)
                .build();
    }
}