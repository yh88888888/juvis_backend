package com.juvis.juvis.maintenance_vendor;

import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis._core.error.ex.ExceptionApi404;
import com.juvis.juvis.maintenance.Maintenance;
import com.juvis.juvis.maintenance.MaintenanceRepository;
import com.juvis.juvis.maintenance.MaintenanceResponse;
import com.juvis.juvis.maintenance_photo.MaintenancePhoto;
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
    private final MaintenancePhotoRepository maintenancePhotoRepository;
    private final PresignService presignService;

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
        Long vendorId = Long.valueOf(loginUser.id());

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

        return toDetailDTO(m);
    }

    private List<String> buildAttachPhotoUrls(Maintenance m) {
        if (m == null || m.getId() == null)
            return List.of();

        List<MaintenancePhoto> photos = maintenancePhotoRepository.findByMaintenanceId(m.getId());

        if (photos == null || photos.isEmpty())
            return List.of();

        return photos.stream()
                .map(MaintenancePhoto::getFileKey)
                .map(presignService::presignedGetUrl)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public MaintenanceResponse.DetailDTO toDetailDTO(Maintenance m) {
        return new MaintenanceResponse.DetailDTO(
                m,
                buildAttachPhotoUrls(m));
    }
}
