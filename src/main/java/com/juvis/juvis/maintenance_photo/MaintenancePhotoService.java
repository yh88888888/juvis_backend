package com.juvis.juvis.maintenance_photo;

import org.springframework.stereotype.Service;

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

        // 권한 체크 예시(지점 기준)
        if (!m.getBranch().getId().equals(user.getBranch().getId())) {
            throw new ExceptionApi403("권한 없음");
        }

        MaintenancePhoto photo = MaintenancePhoto.builder()
                .maintenance(m)
                .fileKey(dto.getFileKey())
                .url(dto.getUrl())
                .build();

        photoRepository.save(photo);
    }
}
