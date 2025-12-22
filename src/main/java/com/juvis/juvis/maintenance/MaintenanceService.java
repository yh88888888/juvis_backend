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
import com.juvis.juvis._core.error.ex.ExceptionApi404;
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
        return userRepository.findById(loginUser.id())
                .orElseThrow(() -> new ExceptionApi400("유저를 찾을 수 없습니다. id=" + loginUser.id()));
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

    @Transactional(readOnly = true)
    public Maintenance getDetailForHq(LoginUser user, Long id) {
        return maintenanceRepository.findDetailForHq(id)
                .orElseThrow(() -> new ExceptionApi404("해당 요청이 없습니다."));
    }

    @Transactional(readOnly = true)
    public MaintenanceResponse.DetailDTO getDetailDtoForHq(LoginUser currentUser, Long id) {
        if (currentUser == null || currentUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }

        // ✅ LAZY 연관(Branch/User들) 미리 로딩된 엔티티
        Maintenance m = maintenanceRepository.findDetailById(id)
                .orElseThrow(() -> new ExceptionApi404("해당 요청이 없습니다."));

        // ✅ 너가 이미 만들어 둔 공용 DTO 변환 사용 (fileKey → presigned url)
        return toDetailDTO(m);
    }

     // ----------------------------
    // HQ: 1차 승인 (REQUESTED -> ESTIMATING)
    // ----------------------------
    @Transactional
    public Maintenance approveRequest(LoginUser currentUser, Long id, MaintenanceRequest.ApproveDTO dto) {
        if (currentUser == null || currentUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }

        Maintenance m = findByIdOrThrow(id);

        if (m.getStatus() != MaintenanceStatus.REQUESTED) {
            throw new ExceptionApi400("REQUESTED 상태에서만 1차 승인할 수 있습니다.");
        }

        User hqUser = loadUser(currentUser);

        m.setStatus(MaintenanceStatus.ESTIMATING);

        // ✅ 결정 기록(승인/반려 공통)
        m.setRequestApprovedBy(hqUser);
        m.setRequestApprovedAt(LocalDateTime.now());

        // 1차 승인 시 1차 반려 사유 제거
        m.setRequestRejectedReason(null);

        return m;
    }


    // ----------------------------
    // HQ: 2차 승인 (APPROVAL_PENDING -> IN_PROGRESS)
    // ----------------------------
    // ----------------------------
    // HQ: 2차 승인 (APPROVAL_PENDING -> IN_PROGRESS)
    // ----------------------------
    @Transactional
    public Maintenance approveEstimate(LoginUser currentUser, Long id, MaintenanceRequest.ApproveDTO dto) {
        if (currentUser == null || currentUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }

        Maintenance m = findByIdOrThrow(id);

        if (m.getStatus() != MaintenanceStatus.APPROVAL_PENDING) {
            throw new ExceptionApi400("APPROVAL_PENDING 상태에서만 2차 승인할 수 있습니다.");
        }

        User hqUser = loadUser(currentUser);

        m.setStatus(MaintenanceStatus.IN_PROGRESS);

        // ✅ 결정 기록(승인/반려 공통)
        m.setEstimateApprovedBy(hqUser);
        m.setEstimateApprovedAt(LocalDateTime.now());

        // 2차 승인 시 2차 반려 사유 제거
        m.setEstimateRejectedReason(null);

        return m;
    }

    // ----------------------------
    // HQ: 1차 반려 (REQUESTED -> HQ1_REJECTED)
    // ----------------------------
     // ----------------------------
    // HQ: 1차 반려 (REQUESTED -> HQ1_REJECTED)
    // ----------------------------
    @Transactional
    public Maintenance rejectRequest(LoginUser currentUser, Long id, MaintenanceRequest.RejectDTO dto) {
        if (currentUser == null || currentUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }
        if (dto == null || dto.getReason() == null || dto.getReason().isBlank()) {
            throw new ExceptionApi400("반려 사유(reason)가 필요합니다.");
        }

        Maintenance m = findByIdOrThrow(id);

        if (m.getStatus() != MaintenanceStatus.REQUESTED) {
            throw new ExceptionApi400("REQUESTED 상태에서만 1차 반려 가능합니다.");
        }

        User hqUser = loadUser(currentUser);

        m.setStatus(MaintenanceStatus.HQ1_REJECTED);
        m.setRequestRejectedReason(dto.getReason().trim());

        // ✅ 반려여도 “결정자/결정일” 찍히게
        m.setRequestApprovedBy(hqUser);
        m.setRequestApprovedAt(LocalDateTime.now());

        return m;
    }


// ----------------------------
    // HQ: 2차 반려 (APPROVAL_PENDING -> HQ2_REJECTED)
    // ----------------------------
    @Transactional
    public Maintenance rejectEstimate(LoginUser currentUser, Long id, MaintenanceRequest.RejectDTO dto) {
        if (currentUser == null || currentUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }
        if (dto == null || dto.getReason() == null || dto.getReason().isBlank()) {
            throw new ExceptionApi400("반려 사유(reason)가 필요합니다.");
        }

        Maintenance m = findByIdOrThrow(id);

        if (m.getStatus() != MaintenanceStatus.APPROVAL_PENDING) {
            throw new ExceptionApi400("APPROVAL_PENDING 상태에서만 2차 반려 가능합니다.");
        }

        User hqUser = loadUser(currentUser);

        m.setStatus(MaintenanceStatus.HQ2_REJECTED);
        m.setEstimateRejectedReason(dto.getReason().trim());

        // ✅ 반려여도 “결정자/결정일” 찍히게
        m.setEstimateApprovedBy(hqUser);
        m.setEstimateApprovedAt(LocalDateTime.now());

        return m;
    }

    @Transactional(readOnly = true)
    public Maintenance findDetailOrThrow(Long id) {
        return maintenanceRepository.findDetailById(id)
                .orElseThrow(() -> new ExceptionApi404("요청 없음"));
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

     // ----------------------------
    // Vendor: 견적 제출 (ESTIMATING -> APPROVAL_PENDING)
    //         견적 재제출 (HQ2_REJECTED -> APPROVAL_PENDING) 단 1회
    // ----------------------------
    @Transactional
    public Maintenance submitEstimate(LoginUser currentUser, Long id, MaintenanceRequest.SubmitEstimateDTO dto) {
        if (currentUser == null || currentUser.role() != UserRole.VENDOR) {
            throw new ExceptionApi403("Vendor 권한이 필요합니다.");
        }
        if (dto == null || dto.getEstimateAmount() == null) {
            throw new ExceptionApi400("estimateAmount가 필요합니다.");
        }

        Maintenance m = findByIdOrThrow(id);

        if (m.getStatus() == MaintenanceStatus.ESTIMATING) {
            // ok
        } else if (m.getStatus() == MaintenanceStatus.HQ2_REJECTED) {
            // 재제출 1회만
            if (m.getEstimateResubmitCount() >= 1) {
                throw new ExceptionApi400("재견적은 1회만 가능합니다.");
            }
            m.setEstimateResubmitCount(m.getEstimateResubmitCount() + 1);
        } else {
            throw new ExceptionApi400("견적 제출 불가 상태입니다. status=" + m.getStatus());
        }

        // (선택) 이 요청에 지정된 vendor만 제출하도록 제한하고 싶으면 여기서 체크
        // 예: if (m.getVendor()==null || !m.getVendor().getId().equals(currentUser.id())) throw...

        m.setEstimateAmount(dto.getEstimateAmount());
        m.setEstimateComment(dto.getEstimateComment());
        m.setWorkStartDate(dto.getWorkStartDate());
        m.setWorkEndDate(dto.getWorkEndDate());

        m.setVendorSubmittedAt(LocalDateTime.now());
        m.setStatus(MaintenanceStatus.APPROVAL_PENDING);

        return m;
    }


     // ----------------------------
    // Vendor: 작업 완료 제출 (IN_PROGRESS -> COMPLETED)
    // ----------------------------
    @Transactional
    public Maintenance completeWork(LoginUser currentUser, Long id, MaintenanceRequest.CompleteWorkDTO dto) {
        if (currentUser == null || currentUser.role() != UserRole.VENDOR) {
            throw new ExceptionApi403("Vendor 권한이 필요합니다.");
        }

        Maintenance m = findByIdOrThrow(id);

        if (m.getStatus() != MaintenanceStatus.IN_PROGRESS) {
            throw new ExceptionApi400("IN_PROGRESS 상태에서만 작업 완료 제출이 가능합니다.");
        }

        m.setResultComment(dto != null ? dto.getResultComment() : null);
        m.setResultPhotoUrl(dto != null ? dto.getResultPhotoUrl() : null);
        m.setWorkCompletedAt(LocalDateTime.now()); // actualEndDate 쓰고 싶으면 변환해서 별도 저장

        m.setStatus(MaintenanceStatus.COMPLETED);

        return m;
    }
}
