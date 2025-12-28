package com.juvis.juvis.maintenance_photo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenancePhotoRepository extends JpaRepository<MaintenancePhoto, Long> {

    List<MaintenancePhoto> findByMaintenanceId(Long maintenanceId);

    // ✅ 타입별 조회 (섹션 분리용)
    List<MaintenancePhoto> findByMaintenanceIdAndPhotoType(
            Long maintenanceId,
            MaintenancePhoto.PhotoType photoType
    );
}