package com.juvis.juvis.maintenance_vendor;

import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis._core.error.ex.ExceptionApi404;
import com.juvis.juvis.maintenance.Maintenance;
import com.juvis.juvis.maintenance.MaintenanceRepository;
import com.juvis.juvis.maintenance.MaintenanceResponse;
import com.juvis.juvis.maintenance.MaintenanceService;
import com.juvis.juvis.maintenance_photo.MaintenancePhotoRepository;
import com.juvis.juvis.user.LoginUser;
import com.juvis.juvis.maintenance_photo.PresignService;

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
    private final MaintenanceService maintenanceService;

    private void requireVendor(LoginUser loginUser) {
        if (loginUser == null || loginUser.role() != UserRole.VENDOR) {
            throw new ExceptionApi403("VENDOR 권한이 필요합니다.");
        }
    }

    // Vendor가 보는 상태는 4개만 허용
    private static final EnumSet<MaintenanceStatus> VENDOR_VISIBLE = EnumSet.of(
            MaintenanceStatus.ESTIMATING,
            MaintenanceStatus.HQ2_REJECTED,
            MaintenanceStatus.APPROVAL_PENDING,
            MaintenanceStatus.IN_PROGRESS,
            MaintenanceStatus.COMPLETED);

    public MaintenanceVendorResponse.SummaryDTO getSummary(LoginUser loginUser) {
        requireVendor(loginUser);
        Long vendorId = Long.valueOf(loginUser.id());

        long estimating = maintenanceRepository.countByVendor_IdAndStatus(
                vendorId, MaintenanceStatus.ESTIMATING);
        long hq2Rejected = maintenanceRepository.countByVendor_IdAndStatus(vendorId, MaintenanceStatus.HQ2_REJECTED);
        long approvalPending = maintenanceRepository.countByVendor_IdAndStatus(
                vendorId, MaintenanceStatus.APPROVAL_PENDING);
        long inProgress = maintenanceRepository.countByVendor_IdAndStatus(
                vendorId, MaintenanceStatus.IN_PROGRESS);
        long completed = maintenanceRepository.countByVendor_IdAndStatus(
                vendorId, MaintenanceStatus.COMPLETED);
        return new MaintenanceVendorResponse.SummaryDTO(
                estimating, hq2Rejected, approvalPending, inProgress, completed);
    }

    public MaintenanceVendorResponse.ListDTO getList(LoginUser loginUser, String status) {
        requireVendor(loginUser);
        Long vendorId = Long.valueOf(loginUser.id());

        List<Maintenance> list;

        if (status == null || status.isBlank()) {
            list = maintenanceRepository.findByVendor_IdAndStatusInOrderByCreatedAtDesc(
                    vendorId,
                    VENDOR_VISIBLE.stream().toList());
        } else {
            MaintenanceStatus s = MaintenanceStatus.valueOf(status.trim().toUpperCase());
            if (!VENDOR_VISIBLE.contains(s)) {
                return new MaintenanceVendorResponse.ListDTO(List.of());
            }

            list = maintenanceRepository.findByVendor_IdAndStatusOrderByCreatedAtDesc(vendorId, s);
        }

        return new MaintenanceVendorResponse.ListDTO(
                list.stream().map(MaintenanceVendorResponse.ListItemDTO::new).toList());
    }

    @Transactional(readOnly = true)
    public MaintenanceResponse.DetailDTO getDetail(LoginUser loginUser, Long id) {
        requireVendor(loginUser);
        Integer vendorId = loginUser.id();

        Maintenance m = maintenanceRepository
                .findByIdAndVendor_Id(id, vendorId)
                .orElseThrow(() -> new ExceptionApi404("요청을 찾을 수 없습니다."));

        return maintenanceService.toDetailDTO(m);
    }


}
