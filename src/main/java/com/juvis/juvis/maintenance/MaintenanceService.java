package com.juvis.juvis.maintenance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juvis.juvis._core.enums.MaintenanceCategory;
import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi400;
import com.juvis.juvis._core.error.ex.ExceptionApi401;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis.branch.Branch;
import com.juvis.juvis.maintenance_photo.MaintenancePhoto;
import com.juvis.juvis.maintenance_photo.MaintenancePhotoRepository;
import com.juvis.juvis.maintenance_photo.PresignService;
import com.juvis.juvis.user.LoginUser;
import com.juvis.juvis.user.User;
import com.juvis.juvis.user.UserRepository;
import lombok.extern.slf4j.Slf4j;

import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final UserRepository userRepository;
    private final MaintenancePhotoRepository maintenancePhotoRepository;
    private final PresignService presignService;

    // ---------- 공통 로더 ----------
    private User loadUser(LoginUser loginUser) {
        if (loginUser == null)
            throw new ExceptionApi401("인증 정보가 없습니다.");
        return userRepository.findById(loginUser.id())
                .orElseThrow(() -> new ExceptionApi401("사용자를 찾을 수 없습니다."));
    }

    private Maintenance findByIdOrThrow(Long id) {
        return maintenanceRepository.findById(id)
                .orElseThrow(() -> new ExceptionApi400("요청을 찾을 수 없습니다. id=" + id));
    }

    // ========================= BRANCH =========================

    @Transactional
    public Maintenance createByBranch(LoginUser loginUser, MaintenanceRequest.CreateDTO dto) {

        if (loginUser.role() != UserRole.BRANCH) {
            throw new ExceptionApi403("지점만 요청서를 생성할 수 있습니다.");
        }

        User currentUser = loadUser(loginUser);
        Branch branch = currentUser.getBranch();

        if (branch == null) {
            throw new ExceptionApi400("지점 정보가 없습니다.");
        }

        Maintenance mr = dto.isSubmit()
                ? Maintenance.createSubmitted(branch, currentUser, dto)
                : Maintenance.createDraft(branch, currentUser, dto);

        Maintenance saved = maintenanceRepository.save(mr);

        // log.info(
        // "CREATE saved id={}, status={}, submit={}, requesterId={}",
        // saved.getId(),
        // saved.getStatus(),
        // dto.isSubmit(),
        // saved.getRequester() != null ? saved.getRequester().getId() : null);

        // ✅ 여기서부터가 핵심
        List<MaintenanceRequest.PhotoDTO> photos = dto.getPhotos();
        if (photos != null && !photos.isEmpty()) {
            List<MaintenancePhoto> entities = photos.stream()
                    .map(p -> MaintenancePhoto.of(
                            saved,
                            p.getFileKey(),
                            p.getUrl()))
                    .collect(Collectors.toList());
            maintenancePhotoRepository.saveAll(entities);
        }

        // LAZY 초기화
        saved.getBranch().getBranchName();

        return saved;
    }

    @Transactional
    public void submitRequest(LoginUser loginUser, Long requestId) {

        if (loginUser.role() != UserRole.BRANCH) {
            throw new ExceptionApi403("지점만 제출할 수 있습니다.");
        }

        // log.info("submitRequest requestId={}, loginUserId={}", requestId,
        // loginUser.id());
        Maintenance mr = findByIdOrThrow(requestId);
        // log.info("submit target status={}, requesterId={}", mr.getStatus(),
        // mr.getRequester() != null ? mr.getRequester().getId() : null);

        // 지점은 보통 지점 소속이면 제출 가능하게 해도 되지만,
        // 너가 기존에 "본인 작성만" 정책을 쓰고 있어서 그대로 유지
        if (mr.getRequester() == null || !mr.getRequester().getId().equals(loginUser.id())) {
            throw new ExceptionApi403("본인 작성 요청만 제출할 수 있습니다.");
        }

        if (mr.getStatus() != MaintenanceStatus.DRAFT && mr.getStatus() != MaintenanceStatus.HQ1_REJECTED) {
            throw new ExceptionApi400("제출할 수 없는 상태입니다.");
        }

        mr.setStatus(MaintenanceStatus.REQUESTED);
        mr.setSubmittedAt(LocalDateTime.now());

        // ✅ 여기 추가
        mr.getBranch().getBranchName();

    }

    public Page<Maintenance> getBranchList(
            LoginUser loginUser,
            MaintenanceStatus status,
            MaintenanceCategory category,
            Pageable pageable) {

        if (loginUser == null || loginUser.role() != UserRole.BRANCH) {
            throw new ExceptionApi403("지점 계정만 조회할 수 있습니다.");
        }

        User user = loadUser(loginUser);

        Branch branch = user.getBranch();
        if (branch == null) {
            throw new ExceptionApi400("지점 정보가 없습니다.");
        }

        return maintenanceRepository.searchForBranch(branch.getId(), status, category, pageable);
    }

    @Transactional(readOnly = true)
    public MaintenanceResponse.DetailDTO getDetailForBranch(LoginUser loginUser, Long id) {

        if (loginUser == null || loginUser.role() != UserRole.BRANCH) {
            throw new ExceptionApi403("지점만 접근 가능합니다.");
        }

        User user = loadUser(loginUser);
        Branch branch = user.getBranch();
        if (branch == null) {
            throw new ExceptionApi400("지점 정보가 없습니다.");
        }

        Maintenance mr = findByIdOrThrow(id);

        // 지점 소속이면 열람 가능
        if (mr.getBranch() == null || !mr.getBranch().getId().equals(branch.getId())) {
            throw new ExceptionApi403("열람 권한이 없습니다.");
        }

        return toDetailDTO(mr);
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
        return new MaintenanceResponse.DetailDTO(m, buildAttachPhotoUrls(m));
    }
    // ========================= HQ =========================

    public Page<Maintenance> getHqList(
            LoginUser loginUser,
            MaintenanceStatus status,
            MaintenanceCategory category,
            Long branchId,
            Pageable pageable) {

        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }

        return maintenanceRepository.searchForHq(status, category, branchId, pageable);
    }

    public Maintenance getDetailForHq(LoginUser loginUser, Long id) {
        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }
        return findByIdOrThrow(id);
    }

    @Transactional
    public Maintenance assignVendor(LoginUser loginUser, Long id, MaintenanceRequest.AssignVendorDTO dto) {
        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }
        if (dto == null || dto.getVendorUserId() == null) {
            throw new ExceptionApi400("vendorUserId가 필요합니다.");
        }

        Maintenance mr = findByIdOrThrow(id);

        // ✅ HQ 1차 액션은 REQUESTED에서만
        if (mr.getStatus() != MaintenanceStatus.REQUESTED) {
            throw new ExceptionApi400("Vendor 지정은 REQUESTED 상태에서만 가능합니다.");
        }

        Long vendorUserId = dto.getVendorUserId();

// Long -> Integer 변환 (범위 체크)
if (vendorUserId > Integer.MAX_VALUE) {
    throw new ExceptionApi400("vendorUserId 범위가 올바르지 않습니다.");
}
Integer vendorIdInt = vendorUserId.intValue();

User vendor = userRepository.findById(vendorIdInt)
        .orElseThrow(() -> new ExceptionApi400("벤더 사용자를 찾을 수 없습니다. id=" + vendorUserId));

        if (vendor.getRole() != UserRole.VENDOR) {
            throw new ExceptionApi400("지정한 사용자가 VENDOR가 아닙니다.");
        }

        mr.setVendor(vendor);

        // ✅ HQ 1차 승인 처리: 승인자/승인일 기록(기존 필드 재사용)
        User hqUser = loadUser(loginUser);
        mr.setApprovedBy(hqUser);
        mr.setApprovedAt(LocalDateTime.now());

        // ✅ 다음 단계로
        mr.setStatus(MaintenanceStatus.ESTIMATING);

        // 1차 승인 이후 반려사유는 비워두는게 안전
        mr.setRejectedReason(null);

        return mr;
    }

    @Transactional
    public Maintenance reject(LoginUser loginUser, Long id, MaintenanceRequest.RejectDTO dto) {
        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }
        if (dto == null || dto.getReason() == null || dto.getReason().isBlank()) {
            throw new ExceptionApi400("반려 사유(reason)가 필요합니다.");
        }

        Maintenance mr = findByIdOrThrow(id);
        User hqUser = loadUser(loginUser);

        // ✅ 1차 반려: REQUESTED에서 반려하면 지점으로 돌아감
        if (mr.getStatus() == MaintenanceStatus.REQUESTED) {
            mr.setStatus(MaintenanceStatus.HQ1_REJECTED);
            mr.setRejectedReason(dto.getReason());

            mr.setApprovedBy(hqUser); // 누가 반려했는지 흔적
            mr.setApprovedAt(LocalDateTime.now()); // 기존 approvedAt 재사용(결정 시각)
            return mr;
        }

        // ✅ 2차 반려: APPROVAL_PENDING에서 반려하면 벤더 재제출(1회) 가능 상태
        if (mr.getStatus() == MaintenanceStatus.APPROVAL_PENDING) {
            mr.setStatus(MaintenanceStatus.HQ2_REJECTED);
            mr.setRejectedReason(dto.getReason());

            mr.setApprovedBy(hqUser);
            mr.setApprovedAt(LocalDateTime.now());
            return mr;
        }

        throw new ExceptionApi400("현재 상태에서는 반려할 수 없습니다. status=" + mr.getStatus());
    }

    @Transactional
    public Maintenance approve(LoginUser loginUser, Long id, MaintenanceRequest.ApproveDTO dto) {
        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }

        Maintenance mr = findByIdOrThrow(id);

        // ✅ HQ 승인(2차)은 벤더가 견적 제출한 후에만
        if (mr.getStatus() != MaintenanceStatus.APPROVAL_PENDING) {
            throw new ExceptionApi400("승인은 APPROVAL_PENDING 상태에서만 가능합니다.");
        }

        User hqUser = loadUser(loginUser);

        mr.setStatus(MaintenanceStatus.IN_PROGRESS);
        mr.setApprovedBy(hqUser);
        mr.setApprovedAt(LocalDateTime.now());
        mr.setRejectedReason(null); // 승인했으니 반려사유 초기화

        return mr;
    }

    // ========================= VENDOR =========================

    public List<Maintenance> findForVendor(LoginUser loginUser, String status) {
    if (loginUser == null || loginUser.role() != UserRole.VENDOR) {
        throw new ExceptionApi403("VENDOR 권한이 필요합니다.");
    }

    User vendor = loadUser(loginUser);

    // status 없으면 전체
    if (status == null || status.isBlank()) {
        return maintenanceRepository.findByVendor(vendor);
    }

    MaintenanceStatus s;
    try {
        s = MaintenanceStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
        throw new ExceptionApi400("잘못된 status 값입니다: " + status);
    }

    // ✅ Vendor가 볼 수 있는 상태만 허용
    if (s != MaintenanceStatus.ESTIMATING &&
        s != MaintenanceStatus.HQ2_REJECTED &&
        s != MaintenanceStatus.IN_PROGRESS &&
        s != MaintenanceStatus.COMPLETED &&
        s != MaintenanceStatus.APPROVAL_PENDING // (필요시) 제출 후 대기까지 Vendor 화면에 보이게 할지 결정
    ) {
        throw new ExceptionApi403("해당 상태는 Vendor 조회 대상이 아닙니다.");
    }

    return maintenanceRepository.findByVendorAndStatus(vendor, s);
}


    @Transactional
    public Maintenance submitEstimate(LoginUser loginUser, Long id, MaintenanceRequest.SubmitEstimateDTO dto) {
        if (loginUser == null || loginUser.role() != UserRole.VENDOR) {
            throw new ExceptionApi403("VENDOR 권한이 필요합니다.");
        }
        if (dto == null) {
            throw new ExceptionApi400("요청 바디가 필요합니다.");
        }

        User vendor = loadUser(loginUser);
        Maintenance mr = findByIdOrThrow(id);

        // ✅ 내게 배정된 건만
        if (mr.getVendor() == null || !mr.getVendor().getId().equals(vendor.getId())) {
            throw new ExceptionApi403("해당 요청에 대한 벤더 권한이 없습니다.");
        }

        // ✅ 최초 제출: ESTIMATING
        // ✅ 재제출: HQ2_REJECTED (단 1회)
        if (mr.getStatus() == MaintenanceStatus.ESTIMATING) {
            // ok
        } else if (mr.getStatus() == MaintenanceStatus.HQ2_REJECTED) {
            if (mr.getEstimateResubmitCount() >= 1) {
                throw new ExceptionApi400("견적 재제출은 1회만 가능합니다.");
            }
            mr.setEstimateResubmitCount(mr.getEstimateResubmitCount() + 1);
        } else {
            throw new ExceptionApi400("견적 제출이 불가능한 상태입니다. status=" + mr.getStatus());
        }

        // ✅ 견적/일정 업데이트
        mr.setEstimateAmount(dto.getEstimateAmount());
        mr.setEstimateComment(dto.getEstimateComment());
        mr.setWorkStartDate(dto.getWorkStartDate());
        mr.setWorkEndDate(dto.getWorkEndDate());

        mr.setVendorSubmittedAt(LocalDateTime.now());
        mr.setStatus(MaintenanceStatus.APPROVAL_PENDING);

        // 제출했으니 기존 반려사유는 지우는게 안전
        mr.setRejectedReason(null);

        return mr;
    }

    @Transactional
public Maintenance completeWork(LoginUser loginUser, Long id, MaintenanceRequest.CompleteWorkDTO dto) {
    if (loginUser == null || loginUser.role() != UserRole.VENDOR) {
        throw new ExceptionApi403("VENDOR 권한이 필요합니다.");
    }
    if (dto == null) {
        throw new ExceptionApi400("요청 바디가 필요합니다.");
    }

    User vendor = loadUser(loginUser);
    Maintenance mr = findByIdOrThrow(id);

    if (mr.getVendor() == null || !mr.getVendor().getId().equals(vendor.getId())) {
        throw new ExceptionApi403("해당 요청에 대한 벤더 권한이 없습니다.");
    }

    if (mr.getStatus() != MaintenanceStatus.IN_PROGRESS) {
        throw new ExceptionApi400("작업 완료 제출은 IN_PROGRESS 상태에서만 가능합니다.");
    }

    mr.setResultComment(dto.getResultComment());
    mr.setResultPhotoUrl(dto.getResultPhotoUrl());

    // dto의 actualEndDate(LocalDate) 대신 엔티티는 LocalDateTime workCompletedAt이므로 now로 처리(최소 변경)
    mr.setWorkCompletedAt(LocalDateTime.now());

    mr.setStatus(MaintenanceStatus.COMPLETED);

    return mr;
}
}
