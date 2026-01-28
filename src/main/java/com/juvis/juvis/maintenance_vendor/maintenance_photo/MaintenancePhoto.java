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

    public enum PhotoType {
        REQUEST,   // 지점 요청 첨부
        ESTIMATE,  // 벤더 견적 첨부
        RESULT     // 벤더 완료 사진
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_id", nullable = false)
    private Maintenance maintenance;

    @Column(nullable = false, length = 500)
    private String fileKey;

    @Column(nullable = false, length = 1000)
    private String publicUrl;

    // REQUEST/RESULT는 null, ESTIMATE만 1 또는 2
    @Column(name = "attempt_no")
    private Integer attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "photo_type", nullable = false, length = 20)
    private PhotoType photoType;

    /**
     * ✅ 공용 팩토리: REQUEST / RESULT 용
     */
    public static MaintenancePhoto of(
            Maintenance maintenance,
            String fileKey,
            String publicUrl,
            PhotoType photoType
    ) {
        // ESTIMATE는 attemptNo가 필요하니 여기로 만들지 않게 방어
        if (photoType == PhotoType.ESTIMATE) {
            throw new IllegalArgumentException("ESTIMATE photo는 ofEstimate()를 사용하세요.");
        }

        return MaintenancePhoto.builder()
                .maintenance(maintenance)
                .fileKey(fileKey)
                .publicUrl(publicUrl)
                .attemptNo(null)
                .photoType(photoType)
                .build();
    }

    /**
     * ✅ 견적 사진 전용 팩토리: attemptNo 필수
     */
    public static MaintenancePhoto ofEstimate(
            Maintenance maintenance,
            String fileKey,
            String publicUrl,
            int attemptNo
    ) {
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo는 1 이상이어야 합니다. attemptNo=" + attemptNo);
        }

        return MaintenancePhoto.builder()
                .maintenance(maintenance)
                .fileKey(fileKey)
                .publicUrl(publicUrl)
                .attemptNo(attemptNo)
                .photoType(PhotoType.ESTIMATE)
                .build();
    }
}
