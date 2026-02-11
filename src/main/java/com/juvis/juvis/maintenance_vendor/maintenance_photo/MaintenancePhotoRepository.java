package com.juvis.juvis.maintenance_vendor.maintenance_photo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface MaintenancePhotoRepository extends JpaRepository<MaintenancePhoto, Long> {

    List<MaintenancePhoto> findByMaintenanceId(Long maintenanceId);

    // ✅ 정렬 보장(프론트 썸네일 순서 고정)
    List<MaintenancePhoto> findByMaintenanceIdOrderByIdAsc(Long maintenanceId);

    // ✅ 타입별 조회 (섹션 분리용)
    List<MaintenancePhoto> findByMaintenanceIdAndPhotoType(
            Long maintenanceId,
            MaintenancePhoto.PhotoType photoType);

    // ✅ 타입별 + 정렬
    List<MaintenancePhoto> findByMaintenanceIdAndPhotoTypeOrderByIdAsc(
            Long maintenanceId,
            MaintenancePhoto.PhotoType photoType);

    // ✅ (선택) 재제출/중복 방지용: attemptNo별 ESTIMATE 사진 삭제
    @Transactional
    void deleteByMaintenanceIdAndPhotoTypeAndAttemptNo(
            Long maintenanceId,
            MaintenancePhoto.PhotoType photoType,
            Integer attemptNo);
}
