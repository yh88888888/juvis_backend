package com.juvis.juvis.maintenance_vendor;

import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis.maintenance.Maintenance;
import com.juvis.juvis.maintenance.MaintenanceRepository;
import com.juvis.juvis.user.LoginUser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaintenanceVendorService {

    private final MaintenanceRepository maintenanceRepository;

    private void requireVendor(LoginUser loginUser) {
        if (loginUser == null || loginUser.role() != UserRole.VENDOR) {
            throw new ExceptionApi403("VENDOR 권한이 필요합니다.");
        }
    }

    // Vendor가 보는 상태는 4개만 허용
    private static final EnumSet<MaintenanceStatus> VENDOR_VISIBLE = EnumSet.of(
            MaintenanceStatus.ESTIMATING,
            MaintenanceStatus.APPROVAL_PENDING,
            MaintenanceStatus.IN_PROGRESS,
            MaintenanceStatus.COMPLETED);

    public MaintenanceVendorResponse.SummaryDTO getSummary(LoginUser loginUser) {
        requireVendor(loginUser);
        Integer vendorId = loginUser.id(); // ✅ 그대로 사용

        long estimating = maintenanceRepository.countByVendor_IdAndStatus(
                vendorId, MaintenanceStatus.ESTIMATING);
        long approvalPending = maintenanceRepository.countByVendor_IdAndStatus(
                vendorId, MaintenanceStatus.APPROVAL_PENDING);
        long inProgress = maintenanceRepository.countByVendor_IdAndStatus(
                vendorId, MaintenanceStatus.IN_PROGRESS);
        long completed = maintenanceRepository.countByVendor_IdAndStatus(
                vendorId, MaintenanceStatus.COMPLETED);
        return new MaintenanceVendorResponse.SummaryDTO(
                estimating, approvalPending, inProgress, completed);
    }

    public MaintenanceVendorResponse.ListDTO getList(LoginUser loginUser, String status) {
        requireVendor(loginUser);
        Integer vendorId = loginUser.id();

        List<Maintenance> list;

        if (status == null || status.isBlank()) {
            // ✅ 전체(단, vendor 가시 상태만)
            list = maintenanceRepository.findByVendorIdAndStatusInOrderByCreatedAtDesc(
                    vendorId,
                    VENDOR_VISIBLE.stream().toList());
        } else {
            MaintenanceStatus s = MaintenanceStatus.valueOf(status.trim().toUpperCase());
            if (!VENDOR_VISIBLE.contains(s)) {
                // vendor가 볼 수 없는 상태 요청이면 빈 리스트로 처리(또는 400으로 던져도 됨)
                return new MaintenanceVendorResponse.ListDTO(List.of());
            }

            list = maintenanceRepository.findByVendorIdAndStatusOrderByCreatedAtDesc(vendorId, s);
        }

        return new MaintenanceVendorResponse.ListDTO(
                list.stream().map(MaintenanceVendorResponse.ListItemDTO::new).toList());
    }
}
