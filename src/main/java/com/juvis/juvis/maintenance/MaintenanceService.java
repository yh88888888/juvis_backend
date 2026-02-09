package com.juvis.juvis.maintenance;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.text.DecimalFormat;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juvis.juvis._core.enums.MaintenanceCategory;
import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi400;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis._core.error.ex.ExceptionApi404;
import com.juvis.juvis.branch.Branch;
import com.juvis.juvis.branch.BranchRepository;
import com.juvis.juvis.maintenance_estimate.MaintenanceEstimateAttempt;
import com.juvis.juvis.maintenance_estimate.MaintenanceEstimateAttemptRepository;
import com.juvis.juvis.maintenance_vendor.maintenance_photo.MaintenancePhoto;
import com.juvis.juvis.maintenance_vendor.maintenance_photo.MaintenancePhotoRepository;
import com.juvis.juvis.maintenance_vendor.maintenance_photo.PresignService;
import com.juvis.juvis.notification.NotificationService;
import com.juvis.juvis.user.LoginUser;
import com.juvis.juvis.user.User;
import com.juvis.juvis.user.UserRepository;
import com.juvis.juvis.vendor_worker.VendorWorker;
import com.juvis.juvis.vendor_worker.VendorWorkerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final VendorWorkerRepository vendorWorkerRepository;
    private final BranchRepository branchRepository;
    private final DailySequenceRepository dailySequenceRepository;

    // ---------- 공통 로더 ----------
    private User loadUser(LoginUser loginUser) {
        return userRepository.findById(loginUser.id())
                .orElseThrow(() -> new ExceptionApi400("유저를 찾을 수 없습니다. id=" + loginUser.id()));
    }

    private Maintenance findByIdOrThrow(Long id) {
        return maintenanceRepository.findById(id)
                .orElseThrow(() -> new ExceptionApi400("요청을 찾을 수 없습니다. id=" + id));
    }

    private static final DateTimeFormatter REQ_NO_FMT = DateTimeFormatter.ofPattern("yyMMdd");

    private String generateRequestNo() {
        LocalDate today = LocalDate.now();

        dailySequenceRepository.upsertAndIncrement(today);
        int seq = dailySequenceRepository.lastSequence(); // 1,2,3...

        if (seq > 999) {
            throw new ExceptionApi400("일일 최대 요청 수(999건)를 초과했습니다.");
        }

        return today.format(REQ_NO_FMT) + "-" + String.format("%03d", seq);
    }

    // ========================= BRANCH =========================
    private String normalizeTitle(String title, MaintenanceCategory category, String description) {
        String t = (title == null) ? "" : title.trim();
        if (!t.isEmpty())
            return t;

        String cat = (category == null) ? "유지보수" : category.getDisplayName();

        String d = (description == null) ? "" : description.trim();
        if (d.isEmpty())
            return cat + " 요청";

        String firstLine = d.split("\\R", 2)[0].trim();
        if (firstLine.isEmpty())
            return cat + " 요청";

        if (firstLine.length() > 30)
            firstLine = firstLine.substring(0, 30) + "…";
        return cat + " - " + firstLine;
    }

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

        dto.setTitle(normalizeTitle(dto.getTitle(), dto.getCategory(), dto.getDescription()));

        Maintenance mr = dto.isSubmit()
                ? Maintenance.createSubmitted(branch, currentUser, dto)
                : Maintenance.createDraft(branch, currentUser, dto);

        mr.setRequestNo(generateRequestNo());

        Maintenance saved = maintenanceRepository.save(mr);

        // ✅ 요청 첨부 사진은 maintenance_photo에 REQUEST로 저장
        List<MaintenanceRequest.PhotoDTO> photos = dto.getPhotos();
        if (photos != null && !photos.isEmpty()) {

            List<MaintenancePhoto> entities = photos.stream()
                    // 🔒 안전 필터 (핵심)
                    .filter(p -> p.getFileKey() != null && !p.getFileKey().isBlank())
                    .filter(p -> p.getUrl() != null && !p.getUrl().isBlank())
                    .map(p -> MaintenancePhoto.of(
                            saved,
                            p.getFileKey().trim(),
                            p.getUrl().trim(),
                            MaintenancePhoto.PhotoType.REQUEST))
                    .toList();

            if (!entities.isEmpty()) {
                maintenancePhotoRepository.saveAll(entities);
            }
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

        return toBranchDetailDTO(mr);
    }

    public MaintenanceResponse.DetailDTO toBranchDetailDTO(Maintenance m) {

        List<String> requestPhotoUrls = buildRequestPhotoUrls(m);
        List<String> resultPhotoUrls = buildResultPhotoUrls(m);

        // ✅ 최신 attempt 1건만 DTO로 만들기 (worker 채우기 목적)
        MaintenanceResponse.EstimateAttemptDTO latestAttempt = findLatestAttemptDto(m.getId());

        List<MaintenanceResponse.EstimateAttemptDTO> attemptsForWorker = (latestAttempt == null) ? List.of()
                : List.of(latestAttempt);

        // ✅ forBranch 내부에서 estimateAttempts를 비워서 반환하도록 처리됨
        return MaintenanceResponse.DetailDTO.forBranch(
                m,
                requestPhotoUrls,
                resultPhotoUrls,
                attemptsForWorker);
    }

    // ========================= 사진 분리 빌더 =========================
    private MaintenanceResponse.EstimateAttemptDTO findLatestAttemptDto(Long maintenanceId) {

        MaintenanceEstimateAttempt latest = attemptRepository
                .findTopByMaintenance_IdOrderByAttemptNoDesc(maintenanceId)
                .orElse(null);

        if (latest == null)
            return null;

        // 최신 attemptNo의 사진만 DTO에 넣어줌
        Map<Integer, List<String>> photoUrlsByAttempt = buildEstimatePhotoUrlsByAttempt(latest.getMaintenance());
        List<String> urls = photoUrlsByAttempt.getOrDefault(latest.getAttemptNo(), List.of());

        return MaintenanceResponse.EstimateAttemptDTO.from(latest, urls);
    }

    private List<String> buildRequestPhotoUrls(Maintenance m) {
        return maintenancePhotoRepository
                .findByMaintenanceIdAndPhotoTypeOrderByIdAsc(
                        m.getId(),
                        MaintenancePhoto.PhotoType.REQUEST)
                .stream()
                .map(MaintenancePhoto::getFileKey)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .map(k -> presignService.presignedGetUrl(k)) // ✅ 여기 핵심
                .toList();
    }

    private List<String> buildResultPhotoUrls(Maintenance m) {
        return maintenancePhotoRepository
                .findByMaintenanceIdAndPhotoTypeOrderByIdAsc(
                        m.getId(),
                        MaintenancePhoto.PhotoType.RESULT)
                .stream()
                .map(MaintenancePhoto::getFileKey)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .map(k -> presignService.presignedGetUrl(k)) // ✅ 여기 핵심
                .toList();
    }

    // ✅ 견적 사진(ESTIMATE) attemptNo별 URL 그룹핑 (view URL)
    private Map<Integer, List<String>> buildEstimatePhotoUrlsByAttempt(Maintenance m) {
        return maintenancePhotoRepository
                .findByMaintenanceIdAndPhotoTypeOrderByIdAsc(m.getId(), MaintenancePhoto.PhotoType.ESTIMATE)
                .stream()
                .filter(p -> p.getAttemptNo() != null)
                .collect(Collectors.groupingBy(
                        MaintenancePhoto::getAttemptNo,
                        Collectors.mapping(
                                p -> presignService.presignedGetUrl(p.getFileKey()),
                                Collectors.toList())));
    }

    // ✅ 견적 사진(ESTIMATE) attemptNo별 DTO 그룹핑 (fileKey + viewUrl)
    private Map<Integer, List<MaintenanceResponse.EstimateAttemptDTO.EstimatePhotoDTO>> buildEstimatePhotosByAttempt(
            Maintenance m) {

        return maintenancePhotoRepository
                .findByMaintenanceIdAndPhotoTypeOrderByIdAsc(m.getId(), MaintenancePhoto.PhotoType.ESTIMATE)
                .stream()
                .filter(p -> p.getAttemptNo() != null)
                .collect(Collectors.groupingBy(
                        MaintenancePhoto::getAttemptNo,
                        Collectors.mapping(
                                p -> new MaintenanceResponse.EstimateAttemptDTO.EstimatePhotoDTO(
                                        p.getFileKey(),
                                        presignService.presignedGetUrl(p.getFileKey())),
                                Collectors.toList())));
    }

    // ✅ HQ/VENDOR 상세 DTO
    public MaintenanceResponse.DetailDTO toDetailDTO(Maintenance m) {

        List<String> requestPhotoUrls = buildRequestPhotoUrls(m);
        List<String> resultPhotoUrls = buildResultPhotoUrls(m);

        Map<Integer, List<String>> estimatePhotoUrlsByAttempt = buildEstimatePhotoUrlsByAttempt(m);
        Map<Integer, List<MaintenanceResponse.EstimateAttemptDTO.EstimatePhotoDTO>> estimatePhotosByAttempt = buildEstimatePhotosByAttempt(
                m);

        List<MaintenanceResponse.EstimateAttemptDTO> attempts = attemptRepository
                .findByMaintenance_IdOrderByAttemptNoAsc(m.getId())
                .stream()
                .map(a -> MaintenanceResponse.EstimateAttemptDTO.from(
                        a,
                        estimatePhotoUrlsByAttempt.getOrDefault(a.getAttemptNo(), List.of()),
                        estimatePhotosByAttempt.getOrDefault(a.getAttemptNo(), List.of())))
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

        // ✅ 승인 코멘트 저장(선택)
        String c = (dto == null || dto.getComment() == null) ? null : dto.getComment().trim();
        m.setRequestApprovedComment((c == null || c.isEmpty()) ? null : c);

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

        // ✅ 최신 attempt
        var current = attempts.get(attempts.size() - 1);

        // ✅ attempt 자체 승인 처리(감사 로그 성격)
        current.approve(loginUser.username());
        attemptRepository.save(current);

        // ✅ 승인자(HQ) 기록
        User hqUser = userRepository.findById(loginUser.id())
                .orElseThrow(() -> new ExceptionApi400("승인자 정보를 찾을 수 없습니다."));

        m.setEstimateApprovedBy(hqUser);
        m.setEstimateApprovedAt(LocalDateTime.now());

        // ✅ 작업예정일 확정 저장
        m.setWorkStartDate(current.getWorkStartDate());
        m.setWorkEndDate(current.getWorkEndDate());

        // ✅ 진행 상태로 전환
        changeStatusWithNotify(m, MaintenanceStatus.IN_PROGRESS);

        // ✅ 기존 반려 사유 초기화(있으면)
        m.setEstimateRejectedReason(null);
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

        // (참고) 기존 코드 유지
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
            changeStatusWithNotify(m, MaintenanceStatus.HQ2_REJECTED);
            m.setEstimateRejectedReason(reason);
        } else if (current.getAttemptNo() == 2) {
            changeStatusWithNotify(m, MaintenanceStatus.ESTIMATE_FINAL_REJECTED);
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

        // 1) 권한 체크
        if (loginUser == null || loginUser.role() != UserRole.VENDOR) {
            throw new ExceptionApi403("Vendor 권한이 필요합니다.");
        }

        // 2) Maintenance 조회
        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ExceptionApi404("요청을 찾을 수 없습니다."));

        // 3) 본인 vendor 요청인지 확인
        if (m.getVendor() == null || !m.getVendor().getId().equals(loginUser.id())) {
            throw new ExceptionApi403("본인에게 배정된 요청만 견적 제출이 가능합니다.");
        }

        // 4) 상태 체크
        boolean canSubmit = m.getStatus() == MaintenanceStatus.ESTIMATING ||
                (m.getStatus() == MaintenanceStatus.HQ2_REJECTED && m.getEstimateResubmitCount() == 0);

        if (!canSubmit) {
            throw new ExceptionApi400("견적 제출 불가 상태입니다. status=" + m.getStatus());
        }

        // 5) attemptNo 결정 (DB 기반)
        List<MaintenanceEstimateAttempt> existing = attemptRepository
                .findByMaintenance_IdOrderByAttemptNoAsc(m.getId());

        int nextAttemptNo = existing.isEmpty()
                ? 1
                : existing.get(existing.size() - 1).getAttemptNo() + 1;

        if (nextAttemptNo > 2) {
            throw new ExceptionApi400("재제출은 1회만 허용됩니다.");
        }

        // 6) 중복 제출 방지
        attemptRepository.findByMaintenance_IdAndAttemptNo(m.getId(), nextAttemptNo)
                .ifPresent(a -> {
                    throw new ExceptionApi400("이미 제출된 견적입니다. attemptNo=" + nextAttemptNo);
                });

        // 7) 필수값 검증(금액)
        if (dto.getEstimateAmount() == null) {
            throw new ExceptionApi400("estimateAmount는 필수입니다.");
        }

        LocalDateTime now = LocalDateTime.now();

        // 7-1) ✅ 작업자 선택 검증 및 반영
        VendorWorker worker = null;

        if (dto.getWorkerId() != null) {
            worker = vendorWorkerRepository
                    .findByIdAndVendorIdAndIsActiveTrue(
                            dto.getWorkerId(),
                            loginUser.id().longValue())
                    .orElseThrow(() -> new ExceptionApi400("유효하지 않은 작업자입니다."));

            m.setVendorWorkerId(worker.getId());
        } else {
            m.setVendorWorkerId(null);
        }

        // 8) attempt 생성/저장
        MaintenanceEstimateAttempt attempt = MaintenanceEstimateAttempt.create(
                m,
                nextAttemptNo,
                dto.getEstimateAmount().toString(),
                dto.getEstimateComment(),
                dto.getWorkStartDate(),
                dto.getWorkEndDate(),
                now);

        // ✅ 스냅샷 저장
        attempt.setWorkerSnapshot(worker);

        attemptRepository.save(attempt);

        // 9) ✅ 견적 사진 메타 저장 (ESTIMATE + attemptNo)
        if (dto.getEstimatePhotos() != null && !dto.getEstimatePhotos().isEmpty()) {

            List<MaintenanceRequest.SubmitEstimateDTO.EstimatePhotoDTO> valid = dto.getEstimatePhotos().stream()
                    .filter(p -> p.getFileKey() != null && !p.getFileKey().isBlank())
                    .filter(p -> p.getPublicUrl() != null && !p.getPublicUrl().isBlank())
                    .toList();

            if (!valid.isEmpty()) {

                // ✅ 꼬임 방지
                maintenancePhotoRepository.deleteByMaintenanceIdAndPhotoTypeAndAttemptNo(
                        m.getId(),
                        MaintenancePhoto.PhotoType.ESTIMATE,
                        nextAttemptNo);

                List<MaintenancePhoto> photos = valid.stream()
                        .map(p -> MaintenancePhoto.ofEstimate(
                                m,
                                p.getFileKey().trim(),
                                p.getPublicUrl().trim(),
                                nextAttemptNo))
                        .toList();

                maintenancePhotoRepository.saveAll(photos);
            }
        }

        // 10) 재제출 카운트는 “2차 저장 성공 후”에만
        if (nextAttemptNo == 2) {
            m.setEstimateResubmitCount(1);
        }

        // 11) 상태/타임스탬프 갱신
        changeStatusWithNotify(m, MaintenanceStatus.APPROVAL_PENDING);
        m.setVendorSubmittedAt(now);
        m.setEstimateRejectedReason(null);
    }

    @Transactional
    public void updateEstimate(LoginUser loginUser, Long maintenanceId, MaintenanceRequest.SubmitEstimateDTO dto) {

        if (loginUser == null || loginUser.role() != UserRole.VENDOR) {
            throw new ExceptionApi403("Vendor 권한이 필요합니다.");
        }

        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ExceptionApi404("요청을 찾을 수 없습니다."));

        if (m.getVendor() == null || !m.getVendor().getId().equals(loginUser.id())) {
            throw new ExceptionApi403("본인에게 배정된 요청만 수정 가능합니다.");
        }

        if (m.getStatus() != MaintenanceStatus.APPROVAL_PENDING) {
            throw new ExceptionApi400("APPROVAL_PENDING 상태에서만 수정 가능합니다. status=" + m.getStatus());
        }

        List<MaintenanceEstimateAttempt> attempts = attemptRepository
                .findByMaintenance_IdOrderByAttemptNoAsc(m.getId());

        if (attempts.isEmpty()) {
            throw new ExceptionApi400("수정할 견적이 없습니다.");
        }

        MaintenanceEstimateAttempt latest = attempts.get(attempts.size() - 1);

        if (latest.getHqDecision() != MaintenanceEstimateAttempt.HqDecision.PENDING) {
            throw new ExceptionApi400("HQ 결정 이후에는 수정할 수 없습니다. decision=" + latest.getHqDecision());
        }

        if (dto.getEstimateAmount() == null) {
            throw new ExceptionApi400("estimateAmount는 필수입니다.");
        }

        // ✅ 작업자 검증 + 스냅샷 갱신
        VendorWorker worker = null;
        if (dto.getWorkerId() != null) {
            worker = vendorWorkerRepository
                    .findByIdAndVendorIdAndIsActiveTrue(dto.getWorkerId(), loginUser.id().longValue())
                    .orElseThrow(() -> new ExceptionApi400("유효하지 않은 작업자입니다."));
            m.setVendorWorkerId(worker.getId());
        } else {
            m.setVendorWorkerId(null);
        }

        latest.setWorkerSnapshot(worker);

        latest.updateEstimate(
                dto.getEstimateAmount().toString().trim(),
                dto.getEstimateComment(),
                dto.getWorkStartDate(),
                dto.getWorkEndDate(),
                worker,
                LocalDateTime.now());

        attemptRepository.save(latest);

        // ✅ 사진 갱신(해당 attemptNo만)
        int attemptNo = latest.getAttemptNo();

        maintenancePhotoRepository.deleteByMaintenanceIdAndPhotoTypeAndAttemptNo(
                m.getId(),
                MaintenancePhoto.PhotoType.ESTIMATE,
                attemptNo);

        if (dto.getEstimatePhotos() != null && !dto.getEstimatePhotos().isEmpty()) {
            List<MaintenanceRequest.SubmitEstimateDTO.EstimatePhotoDTO> valid = dto.getEstimatePhotos().stream()
                    .filter(p -> p.getFileKey() != null && !p.getFileKey().isBlank())
                    .filter(p -> p.getPublicUrl() != null && !p.getPublicUrl().isBlank())
                    .toList();

            List<MaintenancePhoto> photos = valid.stream()
                    .map(p -> MaintenancePhoto.ofEstimate(
                            m,
                            p.getFileKey().trim(),
                            p.getPublicUrl().trim(),
                            attemptNo))
                    .toList();

            if (!photos.isEmpty()) {
                maintenancePhotoRepository.saveAll(photos);
            }
        }

        m.setVendorSubmittedAt(LocalDateTime.now());

        // ✅ 상태 변화 없음: 별도 알림
        notificationService.notifyEstimateUpdated(m);
    }

    @Transactional
    public void editEstimate(LoginUser loginUser, Long maintenanceId, MaintenanceRequest.UpdateEstimateDTO dto) {

        if (loginUser == null || loginUser.role() != UserRole.VENDOR) {
            throw new ExceptionApi403("Vendor 권한이 필요합니다.");
        }

        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ExceptionApi404("요청을 찾을 수 없습니다."));

        if (m.getVendor() == null || !Objects.equals(m.getVendor().getId(), loginUser.id())) {
            throw new ExceptionApi403("본인에게 배정된 요청만 수정 가능합니다.");
        }

        if (m.getStatus() != MaintenanceStatus.APPROVAL_PENDING) {
            throw new ExceptionApi400("수정 불가 상태입니다. status=" + m.getStatus());
        }

        List<MaintenanceEstimateAttempt> attempts = attemptRepository
                .findByMaintenance_IdOrderByAttemptNoAsc(m.getId());

        if (attempts.isEmpty()) {
            throw new ExceptionApi400("수정할 견적(attempt)이 없습니다.");
        }

        MaintenanceEstimateAttempt current = attempts.get(attempts.size() - 1);

        if (current.getHqDecision() != MaintenanceEstimateAttempt.HqDecision.PENDING) {
            throw new ExceptionApi400("HQ 결정 이후에는 수정할 수 없습니다.");
        }

        if (dto == null || dto.getEstimateAmount() == null || dto.getEstimateAmount().trim().isEmpty()) {
            throw new ExceptionApi400("estimateAmount는 필수입니다.");
        }

        VendorWorker worker = null;
        if (dto.getWorkerId() != null) {
            worker = vendorWorkerRepository
                    .findByIdAndVendorIdAndIsActiveTrue(dto.getWorkerId(), loginUser.id().longValue())
                    .orElseThrow(() -> new ExceptionApi400("유효하지 않은 작업자입니다."));
        }

        LocalDateTime now = LocalDateTime.now();

        current.updateEstimate(
                dto.getEstimateAmount().trim(),
                dto.getEstimateComment(),
                dto.getWorkStartDate(),
                dto.getWorkEndDate(),
                worker,
                now);

        boolean photoChanged = Boolean.TRUE.equals(dto.getPhotoChanged());
        if (photoChanged) {
            List<MaintenanceRequest.SubmitEstimateDTO.EstimatePhotoDTO> incoming = (dto.getEstimatePhotos() == null)
                    ? List.of()
                    : dto.getEstimatePhotos();

            List<MaintenanceRequest.SubmitEstimateDTO.EstimatePhotoDTO> valid = incoming.stream()
                    .filter(p -> p.getFileKey() != null && !p.getFileKey().isBlank())
                    .filter(p -> p.getPublicUrl() != null && !p.getPublicUrl().isBlank())
                    .toList();

            maintenancePhotoRepository.deleteByMaintenanceIdAndPhotoTypeAndAttemptNo(
                    m.getId(),
                    MaintenancePhoto.PhotoType.ESTIMATE,
                    current.getAttemptNo());

            if (!valid.isEmpty()) {
                List<MaintenancePhoto> photos = valid.stream()
                        .map(p -> MaintenancePhoto.ofEstimate(
                                m,
                                p.getFileKey().trim(),
                                p.getPublicUrl().trim(),
                                current.getAttemptNo()))
                        .toList();

                maintenancePhotoRepository.saveAll(photos);
            }
        }

        m.setVendorSubmittedAt(now);
        m.setEstimateRejectedReason(null);

        notificationService.notifyEstimateUpdated(m);
    }

    @Transactional
    public MaintenanceResponse.DetailDTO completeWorkAndGetDetail(
            LoginUser currentUser,
            Long id,
            MaintenanceRequest.CompleteWorkDTO dto) {

        Maintenance m = completeWork(currentUser, id, dto);

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
        LocalDateTime completedAt = (dto.getCompletedAt() != null)
                ? dto.getCompletedAt()
                : LocalDateTime.now();

        m.setWorkCompletedAt(completedAt);
        changeStatusWithNotify(m, MaintenanceStatus.COMPLETED);
        // ✅ 최종 견적가(finalAmount) 저장
        if (dto.getFinalAmount() != null) {

            MaintenanceEstimateAttempt latestApprovedAttempt = attemptRepository
                    .findTopByMaintenance_IdAndHqDecisionOrderByAttemptNoDesc(
                            m.getId(),
                            MaintenanceEstimateAttempt.HqDecision.APPROVED)
                    .orElseThrow(() -> new ExceptionApi400("승인된 견적이 없어 최종 견적을 저장할 수 없습니다."));

            latestApprovedAttempt.setFinalAmount(dto.getFinalAmount());

            attemptRepository.save(latestApprovedAttempt);
        }

        // ✅ RESULT 사진 저장
        if (dto.getResultPhotos() != null && !dto.getResultPhotos().isEmpty()) {
            List<MaintenancePhoto> photos = dto.getResultPhotos().stream()
                    .filter(p -> p.getFileKey() != null && !p.getFileKey().isBlank())
                    .filter(p -> p.getPublicUrl() != null && !p.getPublicUrl().isBlank())
                    .map(p -> MaintenancePhoto.of(
                            m,
                            p.getFileKey().trim(),
                            p.getPublicUrl().trim(),
                            MaintenancePhoto.PhotoType.RESULT))
                    .toList();

            if (!photos.isEmpty()) {
                maintenancePhotoRepository.saveAll(photos);
            }
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

    @Transactional
    public Maintenance createByHq(LoginUser loginUser, MaintenanceRequest.HqCreateDTO dto) {

        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ만 요청서를 생성할 수 있습니다.");
        }

        if (dto.getBranchId() == null) {
            throw new ExceptionApi400("branchId는 필수입니다.");
        }

        User hqUser = loadUser(loginUser);

        Branch branch = branchRepository.findById(dto.getBranchId())
                .orElseThrow(() -> new ExceptionApi404("지점을 찾을 수 없습니다. id=" + dto.getBranchId()));

        String fixedTitle = normalizeTitle(dto.getTitle(), dto.getCategory(), dto.getDescription());

        Maintenance m = Maintenance.createDraft(
                branch,
                hqUser,
                new MaintenanceRequest.CreateDTO() {
                    {
                        setTitle(fixedTitle);
                        setDescription(dto.getDescription());
                        setCategory(dto.getCategory());
                        setSubmit(false);
                        setPhotos(dto.getPhotos());
                    }
                });
        m.setRequestNo(generateRequestNo());
        Maintenance saved = maintenanceRepository.save(m);

        // ✅ 요청 사진 저장 (REQUEST)
        if (dto.getPhotos() != null && !dto.getPhotos().isEmpty()) {
            List<MaintenancePhoto> photos = dto.getPhotos().stream()
                    .filter(p -> p.getFileKey() != null && !p.getFileKey().isBlank())
                    .filter(p -> p.getUrl() != null && !p.getUrl().isBlank())
                    .map(p -> MaintenancePhoto.of(
                            saved,
                            p.getFileKey().trim(),
                            p.getUrl().trim(),
                            MaintenancePhoto.PhotoType.REQUEST))
                    .toList();

            if (!photos.isEmpty()) {
                maintenancePhotoRepository.saveAll(photos);
            }
        }

        saved.getBranch().getBranchName();
        return saved;
    }

    @Transactional
    public void submitRequestByHq(LoginUser loginUser, Long id) {
        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ만 제출할 수 있습니다.");
        }

        Maintenance m = findByIdOrThrow(id);

        if (m.getStatus() != MaintenanceStatus.DRAFT) {
            throw new ExceptionApi400("제출할 수 없는 상태입니다. status=" + m.getStatus());
        }

        User vendor = userRepository.findById(43)
                .orElseThrow(() -> new ExceptionApi404("고정 업체(VENDOR=43) 없음"));
        m.setVendor(vendor);

        m.setSubmittedAt(LocalDateTime.now());

        changeStatusWithNotify(m, MaintenanceStatus.ESTIMATING);

        User hqUser = loadUser(loginUser);
        m.setRequestApprovedBy(hqUser);
        m.setRequestApprovedAt(LocalDateTime.now());
        m.setRequestRejectedReason(null);
    }

    public Page<Maintenance> getOpsList(
            LoginUser loginUser,
            MaintenanceStatus status,
            MaintenanceCategory category,
            Long branchId,
            String yearMonth,
            String completedYearMonth, // ✅ 추가
            String sortField, // ✅ 추가
            Pageable pageable) {
        if (loginUser == null ||
                !(loginUser.role() == UserRole.HQ || loginUser.role() == UserRole.VENDOR)) {
            throw new ExceptionApi403("HQ 또는 VENDOR 권한이 필요합니다.");
        }

        LocalDateTime createdFrom = null;
        LocalDateTime createdTo = null;

        if (yearMonth != null && !yearMonth.isBlank()) {
            YearMonth ym = YearMonth.parse(yearMonth.trim());
            createdFrom = ym.atDay(1).atStartOfDay();
            createdTo = ym.plusMonths(1).atDay(1).atStartOfDay();
        }

        // ✅ 완료월 범위
        LocalDateTime completedFrom = null;
        LocalDateTime completedTo = null;

        if (completedYearMonth != null && !completedYearMonth.isBlank()) {
            YearMonth ym = YearMonth.parse(completedYearMonth.trim());
            completedFrom = ym.atDay(1).atStartOfDay();
            completedTo = ym.plusMonths(1).atDay(1).atStartOfDay();
        }

        // ✅ 정렬 강제: createdAt | workCompletedAt
        Sort sort;
        if ("completedAt".equalsIgnoreCase(sortField) || "workCompletedAt".equalsIgnoreCase(sortField)) {
            sort = Sort.by(Sort.Direction.DESC, "workCompletedAt").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        } else {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        Pageable fixed = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        return maintenanceRepository.searchForOps(
                status, category, branchId,
                createdFrom, createdTo,
                completedFrom, completedTo,
                fixed);
    }

    @Transactional(readOnly = true)
    public MaintenanceResponse.DetailDTO getDetailDtoForOps(LoginUser currentUser, Long id) {

        if (currentUser == null ||
                !(currentUser.role() == UserRole.HQ || currentUser.role() == UserRole.VENDOR)) {
            throw new ExceptionApi403("HQ 또는 VENDOR 권한이 필요합니다.");
        }

        Maintenance m = maintenanceRepository.findDetailById(id)
                .orElseThrow(() -> new ExceptionApi404("해당 요청이 없습니다."));

        return toDetailDTO(m);
    }

    private String titleOnly(Maintenance m) {
        String t = (m.getTitle() == null) ? "" : m.getTitle().trim();
        if (t.isEmpty())
            return "";

        if (m.getCategory() == null)
            return t;

        String cat = m.getCategory().getDisplayName(); // 예: "도장"
        if (cat == null || cat.isBlank())
            return t;

        cat = cat.trim();

        String p1 = cat + " - ";
        String p2 = cat + "-";

        if (t.startsWith(p1))
            return t.substring(p1.length()).trim();
        if (t.startsWith(p2))
            return t.substring(p2.length()).trim();

        return t;
    }

    @Transactional(readOnly = true)
    public byte[] exportOpsExcel(
            LoginUser loginUser,
            MaintenanceStatus status,
            MaintenanceCategory category,
            Long branchId,
            String yearMonth) {

        if (loginUser == null ||
                !(loginUser.role() == UserRole.HQ || loginUser.role() == UserRole.VENDOR)) {
            throw new ExceptionApi403("HQ 또는 VENDOR 권한이 필요합니다.");
        }

        LocalDateTime from = null;
        LocalDateTime to = null;
        if (yearMonth != null && !yearMonth.isBlank()) {
            YearMonth ym = YearMonth.parse(yearMonth.trim());
            from = ym.atDay(1).atStartOfDay();
            to = ym.plusMonths(1).atDay(1).atStartOfDay();
        }

        var pageable = PageRequest.of(0, 20000, Sort.by(Sort.Direction.DESC, "createdAt"));
        var page = maintenanceRepository.searchForOps(
                status,
                category,
                branchId,
                from,
                to,
                null, // completedFrom
                null, // completedTo
                pageable);

        var rows = page.getContent();

        // ✅ (중요) APPROVED attempt를 한 번에 가져와서 Map으로 만든다 (N+1 방지)
        List<Long> mids = rows.stream()
                .map(Maintenance::getId)
                .toList();

        Map<Long, MaintenanceEstimateAttempt> approvedMap = attemptRepository.findLatestApprovedAttempts(mids).stream()
                .collect(Collectors.toMap(
                        a -> a.getMaintenance().getId(),
                        a -> a));

        // ✅ exportOpsExcel() 안에서 try (Workbook wb = new XSSFWorkbook()) { ... } 내부에 그대로
        // 붙여서 사용

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("requests");

            // ================= 스타일들 =================
            Font headerFont = wb.createFont();
            headerFont.setBold(true);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER); // ✅ 헤더 가로 가운데
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER); // ✅ 헤더 세로 가운데

            // ✅ 본문(텍스트) 가운데 정렬
            CellStyle bodyCenter = wb.createCellStyle();
            bodyCenter.setAlignment(HorizontalAlignment.CENTER); // ✅ 가로 가운데
            bodyCenter.setVerticalAlignment(VerticalAlignment.CENTER); // ✅ 세로 가운데
            bodyCenter.setWrapText(true); // ✅ 줄바꿈(길면)

            // ✅ 본문(좌측 정렬) - 내용/작업내용 같은 긴 텍스트용
            CellStyle bodyLeft = wb.createCellStyle();
            bodyLeft.setAlignment(HorizontalAlignment.LEFT);
            bodyLeft.setVerticalAlignment(VerticalAlignment.CENTER);
            bodyLeft.setWrapText(true);

            // ================= 헤더 =================
            String[] headers = {
                    "문서번호",
                    "지점",
                    "분야",
                    "내용",
                    "상태",
                    "요청일시",
                    "(견적)시작일",
                    "(견적)종료일",
                    "완료일시",
                    "소요기간",
                    "견적가",
                    "최종견적가",
                    "작업내용"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22); // ✅ 헤더 높이(세로 가운데 체감)
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            DecimalFormat moneyFmt = new DecimalFormat("#,###");

            // ✅ 셀 생성 헬퍼(정렬 적용)
            java.util.function.BiFunction<Row, Integer, Cell> ccell = (r, idx) -> {
                Cell cell = r.createCell(idx);
                cell.setCellStyle(bodyCenter);
                return cell;
            };
            java.util.function.BiFunction<Row, Integer, Cell> lcell = (r, idx) -> {
                Cell cell = r.createCell(idx);
                cell.setCellStyle(bodyLeft);
                return cell;
            };

            // ================= 데이터 =================
            int rowIdx = 1;

            for (Maintenance m : rows) {
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(20); // ✅ 본문 높이(세로 가운데 체감)

                int c = 0;

                ccell.apply(row, c++).setCellValue(safe(m.getRequestNo()));
                ccell.apply(row, c++).setCellValue(m.getBranch() == null ? "" : safe(m.getBranch().getBranchName()));
                ccell.apply(row, c++).setCellValue(m.getCategory() == null ? "" : m.getCategory().getDisplayName());

                // ✅ 내용/작업내용은 좌측정렬로 보기 좋게(원하면 bodyCenter로 바꿔도 됨)
                lcell.apply(row, c++).setCellValue(safe(titleOnly(m)));

                ccell.apply(row, c++).setCellValue(m.getStatus() == null ? "" : m.getStatus().kr());
                ccell.apply(row, c++).setCellValue(m.getSubmittedAt() == null ? "" : m.getSubmittedAt().format(dtf));
                ccell.apply(row, c++).setCellValue(
                        m.getWorkStartDate() == null ? "" : m.getWorkStartDate().toLocalDate().format(df));
                ccell.apply(row, c++)
                        .setCellValue(m.getWorkEndDate() == null ? "" : m.getWorkEndDate().toLocalDate().format(df));
                ccell.apply(row, c++)
                        .setCellValue(m.getWorkCompletedAt() == null ? "" : m.getWorkCompletedAt().format(dtf));

                // ✅ 소요기간(완료 - 요청) : 일/시간/분
                LocalDateTime submittedAt = m.getSubmittedAt();
                LocalDateTime completedAt = m.getWorkCompletedAt();

                String durationText = "";
                if (submittedAt != null && completedAt != null && !completedAt.isBefore(submittedAt)) {
                    long totalMinutes = ChronoUnit.MINUTES.between(submittedAt, completedAt);

                    long days = totalMinutes / (60 * 24);
                    long hours = (totalMinutes % (60 * 24)) / 60;
                    long minutes = totalMinutes % 60;

                    StringBuilder sb = new StringBuilder();
                    if (days > 0)
                        sb.append(days).append("일 ");
                    if (hours > 0)
                        sb.append(hours).append("시간 ");
                    sb.append(minutes).append("분");
                    durationText = sb.toString().trim();
                }
                ccell.apply(row, c++).setCellValue(durationText);

                MaintenanceEstimateAttempt approvedAttempt = approvedMap.get(m.getId());

                String estimateAmount = "";
                String finalAmount = "";

                if (approvedAttempt != null) {

                    if (approvedAttempt.getEstimateAmount() != null && !approvedAttempt.getEstimateAmount().isBlank()) {
                        try {
                            estimateAmount = moneyFmt.format(
                                    Long.parseLong(approvedAttempt.getEstimateAmount().replaceAll("[^0-9]", "")));
                        } catch (Exception ignored) {
                        }
                    }

                    if (approvedAttempt.getFinalAmount() != null) {
                        finalAmount = moneyFmt.format(approvedAttempt.getFinalAmount());
                    }
                }

                row.createCell(c++).setCellValue(estimateAmount);
                row.createCell(c++).setCellValue(finalAmount);

                lcell.apply(row, c++).setCellValue(safe(m.getResultComment())); // ✅ 작업내용 좌측정렬
            }

            // ================= 컬럼 너비 =================
            int[] widths = {
                    14, 9, 6, 30, 10,
                    17, 14, 14, 17,
                    17, 14, 14, 40
            };
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos); // ✅ 여기서 IOException 가능
            return bos.toByteArray();

        } catch (java.io.IOException e) {
            throw new ExceptionApi400("엑셀 생성 실패(IO): " + e.getMessage());
        } catch (Exception e) {
            throw new ExceptionApi400("엑셀 생성 실패: " + e.getMessage());
        }

    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
