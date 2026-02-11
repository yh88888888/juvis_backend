package com.juvis.juvis.maintenance_vendor.maintenance_photo;

import org.springframework.stereotype.Service;

import com.juvis.juvis._core.error.ex.ExceptionApi400;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis._core.error.ex.ExceptionApi404;
import com.juvis.juvis.maintenance.Maintenance;
import com.juvis.juvis.maintenance.MaintenanceRepository;
import com.juvis.juvis.user.User;
import com.juvis.juvis.user.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MaintenancePhotoService {

    private final MaintenanceRepository maintenanceRepository;
    private final MaintenancePhotoRepository photoRepository;
    private final UserRepository userRepository;

    @Transactional
    public void attach(Long maintenanceId, Integer userId, AttachPhotoRequest dto) {
        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ExceptionApi404("유지보수 요청 없음"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ExceptionApi404("유저 없음"));

        if (dto == null || dto.getFileKey() == null || dto.getFileKey().trim().isEmpty()) {
            throw new ExceptionApi400("fileKey는 필수입니다");
        }
        if (dto.getPhotoType() == null || dto.getPhotoType().trim().isEmpty()) {
            throw new ExceptionApi400("photoType은 필수입니다 (REQUEST/ESTIMATE/RESULT)");
        }

        final MaintenancePhoto.PhotoType type;
        try {
            type = MaintenancePhoto.PhotoType.valueOf(dto.getPhotoType().trim().toUpperCase());
        } catch (Exception e) {
            throw new ExceptionApi400("photoType이 올바르지 않습니다: " + dto.getPhotoType());
        }

        // ✅ 권한/정책은 여기서 확정
        switch (type) {
            case REQUEST -> {
                // 지점 요청 첨부: 같은 지점만
                if (user.getBranch() == null || m.getBranch() == null ||
                        !m.getBranch().getId().equals(user.getBranch().getId())) {
                    throw new ExceptionApi403("권한 없음");
                }

                MaintenancePhoto photo = MaintenancePhoto.of(
                        m,
                        dto.getFileKey().trim(),
                        MaintenancePhoto.PhotoType.REQUEST);
                photoRepository.save(photo);
            }

            case ESTIMATE -> {
                // 벤더 견적 첨부: 벤더 본인만 (프로젝트 정책에 맞게 조정 가능)
                // Maintenance에 vendor가 아직 NULL이면 정책 결정 필요.
                // 보통은 vendor가 지정된 상태에서만 업로드 허용하는 게 안전함.
                if (m.getVendor() == null) {
                    throw new ExceptionApi403("벤더가 지정되지 않아 견적 사진 업로드 불가");
                }
                if (!m.getVendor().getId().equals(user.getId())) {
                    throw new ExceptionApi403("권한 없음 (벤더만 가능)");
                }

                Integer attemptNo = dto.getAttemptNo();
                if (attemptNo == null || attemptNo < 1) {
                    throw new ExceptionApi400("ESTIMATE는 attemptNo(1 또는 2)가 필요합니다");
                }

                // (선택) 같은 attemptNo에 대해 재업로드 시 기존 사진 삭제하고 싶으면 활성화
                // photoRepository.deleteByMaintenanceIdAndPhotoTypeAndAttemptNo(
                // maintenanceId, MaintenancePhoto.PhotoType.ESTIMATE, attemptNo);

                MaintenancePhoto photo = MaintenancePhoto.ofEstimate(
                        m,
                        dto.getFileKey().trim(),
                        attemptNo);
                photoRepository.save(photo);
            }

            case RESULT -> {
                // 완료 사진: 보통 벤더만 (또는 HQ도 허용) - 여기서는 벤더만으로 해둠
                if (m.getVendor() == null) {
                    throw new ExceptionApi403("벤더가 지정되지 않아 완료 사진 업로드 불가");
                }
                if (!m.getVendor().getId().equals(user.getId())) {
                    throw new ExceptionApi403("권한 없음 (벤더만 가능)");
                }

                MaintenancePhoto photo = MaintenancePhoto.of(
                        m,
                        dto.getFileKey().trim(),
                        MaintenancePhoto.PhotoType.RESULT);
                photoRepository.save(photo);
            }
        }
    }
}
