package com.juvis.juvis.maintenance_admin;

import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis.maintenance.Maintenance;
import com.juvis.juvis.maintenance.MaintenanceRepository;
import com.juvis.juvis.user.LoginUser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaintenanceAdminService {

    private final MaintenanceRepository maintenanceRepository;

    private void requireHq(LoginUser loginUser) {
        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }
    }

    public MaintenanceAdminResponse.SummaryDTO getSummary(LoginUser loginUser) {
        requireHq(loginUser);

        long requested = maintenanceRepository.countByStatus(MaintenanceStatus.REQUESTED);
        long estimating = maintenanceRepository.countByStatus(MaintenanceStatus.ESTIMATING);
        long approvalPending = maintenanceRepository.countByStatus(MaintenanceStatus.APPROVAL_PENDING);
        long hq2Rejected = maintenanceRepository.countByStatus(MaintenanceStatus.HQ2_REJECTED);
        long inProgress = maintenanceRepository.countByStatus(MaintenanceStatus.IN_PROGRESS);
        long completed = maintenanceRepository.countByStatus(MaintenanceStatus.COMPLETED);

        return new MaintenanceAdminResponse.SummaryDTO(
                requested, estimating, approvalPending, hq2Rejected,inProgress, completed
        );
    }

    public MaintenanceAdminResponse.ListDTO getList(LoginUser loginUser, String status) {
        requireHq(loginUser);

        List<Maintenance> list;
        if (status == null || status.isBlank()) {
            list = maintenanceRepository.findAllByOrderByCreatedAtDesc();
        } else {
            MaintenanceStatus s = MaintenanceStatus.valueOf(status.trim().toUpperCase());
            list = maintenanceRepository.findByStatusOrderByCreatedAtDesc(s);
        }

        return new MaintenanceAdminResponse.ListDTO(
                list.stream().map(MaintenanceAdminResponse.ListItemDTO::new).toList()
        );
    }
}