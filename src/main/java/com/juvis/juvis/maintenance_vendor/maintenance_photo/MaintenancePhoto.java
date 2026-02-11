package com.juvis.juvis.maintenance_vendor.maintenance_photo;

import com.juvis.juvis.maintenance.Maintenance;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "maintenance_photo")
public class MaintenancePhoto {

    public enum PhotoType { REQUEST, ESTIMATE, RESULT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_id", nullable = false)
    private Maintenance maintenance;

    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    @Column(name = "attempt_no")
    private Integer attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "photo_type", nullable = false, length = 20)
    private PhotoType photoType;

    public static MaintenancePhoto of(
            Maintenance maintenance,
            String fileKey,
            PhotoType photoType
    ) {
        if (photoType == PhotoType.ESTIMATE) {
            throw new IllegalArgumentException("ESTIMATE photo는 ofEstimate() 사용");
        }
        return MaintenancePhoto.builder()
                .maintenance(maintenance)
                .fileKey(fileKey)
                .attemptNo(null)
                .photoType(photoType)
                .build();
    }

    public static MaintenancePhoto ofEstimate(
            Maintenance maintenance,
            String fileKey,
            int attemptNo
    ) {
        if (attemptNo < 1) throw new IllegalArgumentException("attemptNo>=1");
        return MaintenancePhoto.builder()
                .maintenance(maintenance)
                .fileKey(fileKey)
                .attemptNo(attemptNo)
                .photoType(PhotoType.ESTIMATE)
                .build();
    }
}

