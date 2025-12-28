package com.juvis.juvis.maintenance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juvis.juvis._core.enums.MaintenanceCategory;
import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi400;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis._core.error.ex.ExceptionApi404;
import com.juvis.juvis.branch.Branch;
import com.juvis.juvis.maintenance_estimate.MaintenanceEstimateAttempt;
import com.juvis.juvis.maintenance_estimate.MaintenanceEstimateAttemptRepository;
import com.juvis.juvis.maintenance_photo.MaintenancePhoto;
import com.juvis.juvis.maintenance_photo.MaintenancePhotoRepository;
import com.juvis.juvis.maintenance_photo.PresignService;
import com.juvis.juvis.notification.NotificationService;
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
    private final NotificationService notificationService;
    private final MaintenanceEstimateAttemptRepository attemptRepository;

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

        // ✅ 요청 첨부 사진은 maintenance_photo에 REQUEST로 저장
        List<MaintenanceRequest.PhotoDTO> photos = dto.getPhotos();
        if (photos != null && !photos.isEmpty()) {
            List<MaintenancePhoto> entities = photos.stream()
                    .filter(p -> p.getFileKey() != null && p.getUrl() != null)
                    .map(p -> MaintenancePhoto.of(
                            saved,
                            p.getFileKey(),
                            p.getUrl(),
                            MaintenancePhoto.PhotoType.REQUEST))
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

        Maintenance mr = findByIdOrThrow(requestId);

        if (mr.getRequester() == null || !mr.getRequester().getId().equals(loginUser.id())) {
            throw new ExceptionApi403("본인 작성 요청만 제출할 수 있습니다.");
        }

        if (mr.getStatus() != MaintenanceStatus.DRAFT && mr.getStatus() != MaintenanceStatus.HQ1_REJECTED) {
            throw new ExceptionApi400("제출할 수 없는 상태입니다.");
        }

        changeStatusWithNotify(mr, MaintenanceStatus.REQUESTED);
        mr.setSubmittedAt(LocalDateTime.now());

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

        if (mr.getBranch() == null || !mr.getBranch().getId().equals(branch.getId())) {
            throw new ExceptionApi403("열람 권한이 없습니다.");
        }

        return toDetailDTO(mr);
    }

    // ========================= 사진 분리 빌더 =========================

    private List<String> buildRequestPhotoUrls(Maintenance m) {
        return maintenancePhotoRepository
                .findByMaintenanceIdAndPhotoType(m.getId(), MaintenancePhoto.PhotoType.REQUEST)
                .stream()
                .map(MaintenancePhoto::getFileKey)
                .map(presignService::presignedGetUrl) // ✅ 핵심: GET presign으로 viewUrl 생성
                .filter(u -> u != null && !u.isBlank())
                .toList();
    }

    private List<String> buildResultPhotoUrls(Maintenance m) {
        return maintenancePhotoRepository
                .findByMaintenanceIdAndPhotoType(m.getId(), MaintenancePhoto.PhotoType.RESULT)
                .stream()
                .map(MaintenancePhoto::getFileKey)
                .map(presignService::presignedGetUrl) // ✅ 핵심
                .filter(u -> u != null && !u.isBlank())
                .toList();
    }

    // ✅ 상세 DTO
    public MaintenanceResponse.DetailDTO toDetailDTO(Maintenance m) {

        List<String> requestPhotoUrls = buildRequestPhotoUrls(m);
        List<String> resultPhotoUrls = buildResultPhotoUrls(m);

        List<MaintenanceResponse.EstimateAttemptDTO> attempts = attemptRepository
                .findByMaintenance_IdOrderByAttemptNoAsc(m.getId())
                .stream()
                .map(MaintenanceResponse.EstimateAttemptDTO::from)
                .toList();

        return new MaintenanceResponse.DetailDTO(
                m,
                requestPhotoUrls,
                resultPhotoUrls,
                attempts);
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

        Maintenance m = maintenanceRepository.findDetailById(id)
                .orElseThrow(() -> new ExceptionApi404("해당 요청이 없습니다."));

        return toDetailDTO(m);
    }

    // HQ: 1차 승인 (REQUESTED -> ESTIMATING)
    @Transactional
    public Maintenance approveRequest(LoginUser currentUser, Long id, MaintenanceRequest.ApproveDTO dto) {
        if (currentUser == null || currentUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }

        Maintenance m = findByIdOrThrow(id);

        if (m.getStatus() != MaintenanceStatus.REQUESTED) {
            throw new ExceptionApi400("REQUESTED 상태에서만 1차 승인할 수 있습니다.");
        }
        // ✅ 고정 Vendor 자동 배정 (id=43)
        User vendor = userRepository.findById(43)
                .orElseThrow(() -> new ExceptionApi404("고정 업체(VENDOR=43) 없음"));

        m.setVendor(vendor);

        User hqUser = loadUser(currentUser);

        changeStatusWithNotify(m, MaintenanceStatus.ESTIMATING);

        m.setRequestApprovedBy(hqUser);
        m.setRequestApprovedAt(LocalDateTime.now());
        m.setRequestRejectedReason(null);

        return m;
    }

    // HQ: 2차 승인 (APPROVAL_PENDING -> IN_PROGRESS)
    @Transactional
    public void approveEstimate(Long maintenanceId, LoginUser loginUser) {
        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ExceptionApi404("요청을 찾을 수 없습니다."));

        if (m.getStatus() != MaintenanceStatus.APPROVAL_PENDING) {
            throw new ExceptionApi400("승인 불가 상태입니다. status=" + m.getStatus());
        }

        var attempts = attemptRepository.findByMaintenance_IdOrderByAttemptNoAsc(m.getId());
        if (attempts.isEmpty()) {
            throw new ExceptionApi400("견적 데이터가 없습니다.");
        }

        var current = attempts.get(attempts.size() - 1);

        current.approve(loginUser.username());
        attemptRepository.save(current);

        User hqUser = userRepository.findById(loginUser.id())
                .orElseThrow(() -> new ExceptionApi400("승인자 정보를 찾을 수 없습니다."));

        m.setEstimateApprovedBy(hqUser);
        m.setEstimateApprovedAt(LocalDateTime.now());

        m.setStatus(MaintenanceStatus.IN_PROGRESS);
    }

    // HQ: 1차 반려 (REQUESTED -> HQ1_REJECTED)
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

        changeStatusWithNotify(m, MaintenanceStatus.HQ1_REJECTED);
        m.setRequestRejectedReason(dto.getReason().trim());

        m.setRequestApprovedBy(hqUser);
        m.setRequestApprovedAt(LocalDateTime.now());

        return m;
    }

    // HQ: 2차 반려 (APPROVAL_PENDING -> HQ2_REJECTED or ESTIMATE_FINAL_REJECTED)
    @Transactional
    public void rejectEstimate(Long maintenanceId, LoginUser loginUser, String reason) {
        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ExceptionApi404("요청을 찾을 수 없습니다."));

        if (m.getStatus() != MaintenanceStatus.APPROVAL_PENDING) {
            throw new ExceptionApi400("반려 불가 상태입니다. status=" + m.getStatus());
        }

        var attempts = attemptRepository.findByMaintenance_IdOrderByAttemptNoAsc(m.getId());
        if (attempts.isEmpty())
            throw new ExceptionApi400("견적 데이터가 없습니다.");

        var current = attempts.get(attempts.size() - 1);
        current.reject(loginUser.username(), reason);
        attemptRepository.save(current);

        if (current.getAttemptNo() == 1) {
            m.setStatus(MaintenanceStatus.HQ2_REJECTED);
            m.setEstimateRejectedReason(reason);
        } else if (current.getAttemptNo() == 2) {
            m.setStatus(MaintenanceStatus.ESTIMATE_FINAL_REJECTED);
            m.setEstimateRejectedReason(reason);
        } else {
            throw new ExceptionApi400("잘못된 attemptNo=" + current.getAttemptNo());
        }
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

        if (status == null || status.isBlank()) {
            return maintenanceRepository.findByVendor(vendor);
        }

        MaintenanceStatus s;
        try {
            s = MaintenanceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ExceptionApi400("잘못된 status 값입니다: " + status);
        }

        if (s != MaintenanceStatus.ESTIMATING &&
                s != MaintenanceStatus.HQ2_REJECTED &&
                s != MaintenanceStatus.IN_PROGRESS &&
                s != MaintenanceStatus.COMPLETED &&
                s != MaintenanceStatus.APPROVAL_PENDING) {
            throw new ExceptionApi403("해당 상태는 Vendor 조회 대상이 아닙니다.");
        }

        return maintenanceRepository.findByVendorAndStatus(vendor, s);
    }

    @Transactional
    public void submitEstimate(
            LoginUser loginUser,
            Long maintenanceId,
            MaintenanceRequest.SubmitEstimateDTO dto) {

        if (loginUser == null || loginUser.role() != UserRole.VENDOR) {
            throw new ExceptionApi403("Vendor 권한이 필요합니다.");
        }

        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ExceptionApi404("요청을 찾을 수 없습니다."));

        if (m.getVendor() == null || !m.getVendor().getId().equals(loginUser.id())) {
            throw new ExceptionApi403("본인에게 배정된 요청만 견적 제출이 가능합니다.");
        }

        int attemptNo;
        if (m.getStatus() == MaintenanceStatus.ESTIMATING) {
            attemptNo = 1;
        } else if (m.getStatus() == MaintenanceStatus.HQ2_REJECTED
                && m.getEstimateResubmitCount() == 0) {
            attemptNo = 2;
            m.setEstimateResubmitCount(1);
        } else {
            throw new ExceptionApi400("견적 제출 불가 상태입니다. status=" + m.getStatus());
        }

        attemptRepository.findByMaintenance_IdAndAttemptNo(m.getId(), attemptNo)
                .ifPresent(a -> {
                    throw new ExceptionApi400("이미 제출된 견적입니다. attemptNo=" + attemptNo);
                });

        LocalDateTime now = LocalDateTime.now();
        MaintenanceEstimateAttempt attempt = MaintenanceEstimateAttempt.create(
                m,
                attemptNo,
                dto.getEstimateAmount().toString(),
                dto.getEstimateComment(),
                dto.getWorkStartDate(),
                dto.getWorkEndDate(),
                now);
        attemptRepository.save(attempt);

        m.setStatus(MaintenanceStatus.APPROVAL_PENDING);
        m.setVendorSubmittedAt(now);
        m.setEstimateRejectedReason(null);
    }

    @Transactional
    public MaintenanceResponse.DetailDTO completeWorkAndGetDetail(
            LoginUser currentUser,
            Long id,
            MaintenanceRequest.CompleteWorkDTO dto) {

        Maintenance m = completeWork(currentUser, id, dto);

        // ✅ detached 방지: 여기서 다시 find로 당겨오면 더 안전
        Maintenance fresh = maintenanceRepository.findById(m.getId())
                .orElseThrow(() -> new ExceptionApi404("요청을 찾을 수 없습니다."));

        return toDetailDTO(fresh);
    }

    // Vendor: 작업 완료 제출 (IN_PROGRESS -> COMPLETED) + RESULT 사진 저장
    @Transactional
    public Maintenance completeWork(LoginUser currentUser, Long id, MaintenanceRequest.CompleteWorkDTO dto) {

        Maintenance m = maintenanceRepository.findById(id)
                .orElseThrow(() -> new ExceptionApi404("요청을 찾을 수 없습니다."));

        if (m.getVendor() == null || !m.getVendor().getId().equals(currentUser.id())) {
            throw new ExceptionApi403("본인 업체 요청만 처리할 수 있습니다.");
        }

        if (m.getStatus() != MaintenanceStatus.IN_PROGRESS) {
            throw new ExceptionApi403("IN_PROGRESS 상태에서만 완료 제출 가능합니다.");
        }

        String comment = dto.getResultComment() == null ? "" : dto.getResultComment().trim();
        if (comment.isEmpty()) {
            throw new ExceptionApi403("작업 내용(resultComment)은 필수입니다.");
        }

        m.setResultComment(comment);
        m.setWorkCompletedAt(LocalDateTime.now());
        m.setStatus(MaintenanceStatus.COMPLETED);
        maintenanceRepository.save(m);

        if (dto.getResultPhotos() != null && !dto.getResultPhotos().isEmpty()) {
            List<MaintenancePhoto> photos = dto.getResultPhotos().stream()
                    .filter(p -> p.getFileKey() != null && p.getPublicUrl() != null)
                    .map(p -> MaintenancePhoto.of(m, p.getFileKey(), p.getPublicUrl(),
                            MaintenancePhoto.PhotoType.RESULT))
                    .toList();

            maintenancePhotoRepository.saveAll(photos);
        }

        return m;
    }

    private void changeStatusWithNotify(Maintenance m, MaintenanceStatus next) {
        MaintenanceStatus before = m.getStatus();
        if (before == next)
            return;
        m.setStatus(next);
        notificationService.notifyOnStatusChange(m, before, next);
    }
}
