package com.juvis.juvis.maintenance;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
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

import java.io.ByteArrayOutputStream;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;

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
    private final VendorWorkerRepository vendorWorkerRepository;
    private final BranchRepository branchRepository;

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

        // ✅ 최신 attempt 엔티티 1건 조회
        MaintenanceEstimateAttempt latest = attemptRepository
                .findTopByMaintenance_IdOrderByAttemptNoDesc(maintenanceId)
                .orElse(null);

        if (latest == null)
            return null;

        // ✅ 견적 사진 URL 매핑(이미 너 서비스에 있는 함수 재사용)
        // 최신 attemptNo의 사진만 꺼내서 DTO에 넣어줌 (사실 지점은 attempts를 숨길 거라 필수는 아님)
        Map<Integer, List<String>> photoUrlsByAttempt = buildEstimatePhotoUrlsByAttempt(latest.getMaintenance());
        List<String> urls = photoUrlsByAttempt.getOrDefault(latest.getAttemptNo(), List.of());

        return MaintenanceResponse.EstimateAttemptDTO.from(latest, urls);
    }

    private List<String> buildRequestPhotoUrls(Maintenance m) {
        return maintenancePhotoRepository
                .findByMaintenanceIdAndPhotoType(m.getId(), MaintenancePhoto.PhotoType.REQUEST)
                .stream()
                .map(MaintenancePhoto::getFileKey)
                .map(presignService::presignedGetUrl) // ✅ GET presign으로 viewUrl
                .filter(u -> u != null && !u.isBlank())
                .toList();
    }

    private List<String> buildResultPhotoUrls(Maintenance m) {
        return maintenancePhotoRepository
                .findByMaintenanceIdAndPhotoType(m.getId(), MaintenancePhoto.PhotoType.RESULT)
                .stream()
                .map(MaintenancePhoto::getFileKey)
                .map(presignService::presignedGetUrl)
                .filter(u -> u != null && !u.isBlank())
                .toList();
    }

    // ✅ 추가: 견적 사진(ESTIMATE) attemptNo별로 URL을 그룹핑
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

    // ✅ 상세 DTO
    public MaintenanceResponse.DetailDTO toDetailDTO(Maintenance m) {

        List<String> requestPhotoUrls = buildRequestPhotoUrls(m);
        List<String> resultPhotoUrls = buildResultPhotoUrls(m);

        // ✅ 기존 유지
        Map<Integer, List<String>> estimatePhotoUrlsByAttempt = buildEstimatePhotoUrlsByAttempt(m);

        // ✅ 신규 추가
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

        // ✅ 여기 추가: 승인 코멘트 저장(선택)
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

        // ✅ 승인자(HQ) 기록 (레거시 필드)
        User hqUser = userRepository.findById(loginUser.id())
                .orElseThrow(() -> new ExceptionApi400("승인자 정보를 찾을 수 없습니다."));

        m.setEstimateApprovedBy(hqUser);
        m.setEstimateApprovedAt(LocalDateTime.now());

        // ✅ [추천1] 작업예정일 확정 저장 (지점에서 attempts 숨겨도 일정 보이게)
        // - null이면 그냥 null로 남음 (업체가 날짜를 안 넣은 케이스)
        m.setWorkStartDate(current.getWorkStartDate());
        m.setWorkEndDate(current.getWorkEndDate());

        // (선택) 레거시 견적 필드도 유지/동기화하고 싶으면 같이 저장 가능
        // - 지금은 지점에서 견적 금액/코멘트를 숨기므로 필수는 아님
        // m.setEstimateComment(current.getEstimateComment());
        // if (current.getEstimateAmount() != null) {
        // try {
        // m.setEstimateAmount(new BigDecimal(current.getEstimateAmount().replace(",",
        // "").trim()));
        // } catch (Exception ignore) {
        // // 금액 포맷이 이상하면 레거시는 비워둠
        // m.setEstimateAmount(null);
        // }
        // }

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

        // 6) 안전장치(중복 제출 방지)
        attemptRepository.findByMaintenance_IdAndAttemptNo(m.getId(), nextAttemptNo)
                .ifPresent(a -> {
                    throw new ExceptionApi400("이미 제출된 견적입니다. attemptNo=" + nextAttemptNo);
                });

        // 7) 필수값 검증(금액)
        if (dto.getEstimateAmount() == null) {
            throw new ExceptionApi400("estimateAmount는 필수입니다.");
        }

        LocalDateTime now = LocalDateTime.now();

        // =========================
        // 7-1) ✅ 작업자 선택 검증 및 반영
        // =========================
        // (견적 제출 서비스 메서드 내부) - 해당 블록 교체

        VendorWorker worker = null;

        if (dto.getWorkerId() != null) {
            worker = vendorWorkerRepository
                    .findByIdAndVendorIdAndIsActiveTrue(
                            dto.getWorkerId(),
                            loginUser.id().longValue())
                    .orElseThrow(() -> new ExceptionApi400("유효하지 않은 작업자입니다."));

            m.setVendorWorkerId(worker.getId()); // 유지하고 싶으면 OK
        } else {
            m.setVendorWorkerId(null);
        }

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
        // =========================
        // 9) ✅ 견적 사진 메타 저장 (핵심)
        // - 업로드는 이미 프론트에서 S3 PUT 완료
        // - 여기서는 fileKey/publicUrl만 DB에 저장
        // - attemptNo를 함께 저장해서 1차/2차 사진이 섞이지 않게 함
        // =========================
        if (dto.getEstimatePhotos() != null && !dto.getEstimatePhotos().isEmpty()) {

            // (선택) 들어온 값 필터링
            List<MaintenanceRequest.SubmitEstimateDTO.EstimatePhotoDTO> valid = dto.getEstimatePhotos().stream()
                    .filter(p -> p.getFileKey() != null && !p.getFileKey().trim().isEmpty())
                    .filter(p -> p.getPublicUrl() != null && !p.getPublicUrl().trim().isEmpty())
                    .toList();

            if (!valid.isEmpty()) {

                // ✅ 꼬임 방지 포인트:
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

        // ✅ m은 영속 상태라 save() 없어도 커밋 시점에 반영됨
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

        // 필수값 검증
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

        // ✅ attempt 업데이트
        latest.setWorkerSnapshot(worker);

        // amount/comment/date 갱신 (setter가 없다면 entity에 setter 추가하거나 @Setter 일부 허용)
        latest.updateEstimate(
                dto.getEstimateAmount().toString().trim(),
                dto.getEstimateComment(),
                dto.getWorkStartDate(),
                dto.getWorkEndDate(),
                worker, // VendorWorker (없으면 null)
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
                    .filter(p -> p.getFileKey() != null && !p.getFileKey().trim().isEmpty())
                    .filter(p -> p.getPublicUrl() != null && !p.getPublicUrl().trim().isEmpty())
                    .toList();

            List<MaintenancePhoto> photos = valid.stream()
                    .map(p -> MaintenancePhoto.ofEstimate(
                            m,
                            p.getFileKey().trim(),
                            p.getPublicUrl().trim(),
                            attemptNo))
                    .toList();

            maintenancePhotoRepository.saveAll(photos);
        }

        // ✅ maintenance의 vendorSubmittedAt도 갱신(선택이지만 UX상 깔끔)
        m.setVendorSubmittedAt(LocalDateTime.now());
        // =========================================================
        // ✅ [추가] 견적 수정 알림 발생 (상태 변화가 없으므로 별도 이벤트)
        // - NotificationService에 notifyOnEstimateUpdated(Maintenance m)만 구현해주면 됨
        // =========================================================
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

        // ✅ 승인/반려 전(결정 전)만 수정 가능하게: status=APPROVAL_PENDING
        if (m.getStatus() != MaintenanceStatus.APPROVAL_PENDING) {
            throw new ExceptionApi400("수정 불가 상태입니다. status=" + m.getStatus());
        }

        // ✅ 최신 attempt
        List<MaintenanceEstimateAttempt> attempts = attemptRepository
                .findByMaintenance_IdOrderByAttemptNoAsc(m.getId());

        if (attempts.isEmpty()) {
            throw new ExceptionApi400("수정할 견적(attempt)이 없습니다.");
        }

        MaintenanceEstimateAttempt current = attempts.get(attempts.size() - 1);

        // ✅ HQ가 이미 결정을 내렸으면 수정 금지
        if (current.getHqDecision() != MaintenanceEstimateAttempt.HqDecision.PENDING) {
            throw new ExceptionApi400("HQ 결정 이후에는 수정할 수 없습니다.");
        }

        // ✅ 필수 검증
        if (dto == null || dto.getEstimateAmount() == null || dto.getEstimateAmount().trim().isEmpty()) {
            throw new ExceptionApi400("estimateAmount는 필수입니다.");
        }

        // 0) 작업자 검증/조회
        VendorWorker worker = null;
        if (dto.getWorkerId() != null) {
            worker = vendorWorkerRepository
                    .findByIdAndVendorIdAndIsActiveTrue(dto.getWorkerId(), loginUser.id().longValue())
                    .orElseThrow(() -> new ExceptionApi400("유효하지 않은 작업자입니다."));
        }

        LocalDateTime now = LocalDateTime.now();

        // 1) ✅ attempt 본문 수정 (⚠️ builder/save로 새 엔티티 만들지 말 것)
        current.updateEstimate(
                dto.getEstimateAmount().trim(),
                dto.getEstimateComment(),
                dto.getWorkStartDate(),
                dto.getWorkEndDate(),
                worker,
                now);

        // (선택) 영속 상태면 save() 없어도 dirty checking으로 반영됨
        // attemptRepository.save(current);

        // 2) ✅ 사진은 “photoChanged=true”일 때만 교체
        boolean photoChanged = Boolean.TRUE.equals(dto.getPhotoChanged());
        if (photoChanged) {
            List<MaintenanceRequest.SubmitEstimateDTO.EstimatePhotoDTO> incoming = (dto.getEstimatePhotos() == null)
                    ? List.of()
                    : dto.getEstimatePhotos();

            List<MaintenanceRequest.SubmitEstimateDTO.EstimatePhotoDTO> valid = incoming.stream()
                    .filter(p -> p.getFileKey() != null && !p.getFileKey().trim().isEmpty())
                    .filter(p -> p.getPublicUrl() != null && !p.getPublicUrl().trim().isEmpty())
                    .toList();

            // ✅ 전체 교체(삭제+삽입)
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

        // 3) ✅ 상태는 여전히 APPROVAL_PENDING 유지 + 레거시 필드 업데이트(원하면)
        m.setVendorSubmittedAt(now);
        m.setEstimateRejectedReason(null);
        // =========================================================
        // ✅ [추가] 견적 수정 알림 발생 (상태 변화가 없으므로 별도 이벤트)
        // =========================================================
        notificationService.notifyEstimateUpdated(m);

        // (선택) 작업자 ID를 maintenance에도 저장해두는 정책이면 같이 갱신
        // m.setVendorWorkerId(worker == null ? null : worker.getId());
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
        LocalDateTime completedAt = (dto.getCompletedAt() != null)
                ? dto.getCompletedAt()
                : LocalDateTime.now();

        m.setWorkCompletedAt(completedAt);
        changeStatusWithNotify(m, MaintenanceStatus.COMPLETED);
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
                .orElseThrow(() -> new ExceptionApi404(
                        "지점을 찾을 수 없습니다. id=" + dto.getBranchId()));

        // ✅ DRAFT로 생성 (저장 버튼)
        Maintenance m = Maintenance.createDraft(
                branch,
                hqUser, // requester = HQ
                new MaintenanceRequest.CreateDTO() {
                    {
                        setTitle(dto.getTitle());
                        setDescription(dto.getDescription());
                        setCategory(dto.getCategory());
                        setSubmit(false);
                        setPhotos(dto.getPhotos());
                    }
                });

        Maintenance saved = maintenanceRepository.save(m);

        // ✅ 요청 사진 저장 (기존 로직 그대로)
        if (dto.getPhotos() != null && !dto.getPhotos().isEmpty()) {
            List<MaintenancePhoto> photos = dto.getPhotos().stream()
                    .filter(p -> p.getFileKey() != null && p.getUrl() != null)
                    .map(p -> MaintenancePhoto.of(
                            saved,
                            p.getFileKey(),
                            p.getUrl(),
                            MaintenancePhoto.PhotoType.REQUEST))
                    .toList();

            maintenancePhotoRepository.saveAll(photos);
        }

        saved.getBranch().getBranchName(); // lazy 방지
        return saved;
    }

    @Transactional
    public void submitRequestByHq(LoginUser loginUser, Long id) {
        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ만 제출할 수 있습니다.");
        }

        Maintenance m = findByIdOrThrow(id);

        // ✅ HQ + DRAFT면 제출 가능 (requester_id 무관)
        if (m.getStatus() != MaintenanceStatus.DRAFT) {
            throw new ExceptionApi400("제출할 수 없는 상태입니다. status=" + m.getStatus());
        }

        // ✅ vendor 자동배정
        User vendor = userRepository.findById(43)
                .orElseThrow(() -> new ExceptionApi404("고정 업체(VENDOR=43) 없음"));
        m.setVendor(vendor);

        // ✅ 제출 시점 기록
        m.setSubmittedAt(LocalDateTime.now());

        // ✅ 곧바로 견적 단계로
        changeStatusWithNotify(m, MaintenanceStatus.ESTIMATING);

        // ✅ HQ 승인 정보
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
            String yearMonth, // "YYYY-MM"
            Pageable pageable) {

        if (loginUser == null ||
                !(loginUser.role() == UserRole.HQ || loginUser.role() == UserRole.VENDOR)) {
            throw new ExceptionApi403("HQ 또는 VENDOR 권한이 필요합니다.");
        }

        LocalDateTime from = null;
        LocalDateTime to = null;

        if (yearMonth != null && !yearMonth.isBlank()) {
            // "2026-01" 형태
            var ym = java.time.YearMonth.parse(yearMonth.trim());
            from = ym.atDay(1).atStartOfDay();
            to = ym.plusMonths(1).atDay(1).atStartOfDay();
        }

        return maintenanceRepository.searchForOps(status, category, branchId, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public MaintenanceResponse.DetailDTO getDetailDtoForOps(LoginUser currentUser, Long id) {

        if (currentUser == null ||
                !(currentUser.role() == UserRole.HQ || currentUser.role() == UserRole.VENDOR)) {
            throw new ExceptionApi403("HQ 또는 VENDOR 권한이 필요합니다.");
        }

        Maintenance m = maintenanceRepository.findDetailById(id)
                .orElseThrow(() -> new ExceptionApi404("해당 요청이 없습니다."));

        // ✅ HQ/VENDOR는 기존 toDetailDTO 그대로 사용
        // (지점만 숨김 정책은 forBranch에서만 적용 중)
        return toDetailDTO(m);
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
        var page = maintenanceRepository.searchForOps(status, category, branchId, from, to, pageable);
        var rows = page.getContent();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("requests");

            // ================= 헤더 스타일 =================
            Font headerFont = wb.createFont();
            headerFont.setBold(true);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // ================= 헤더 (ID, 요청자 제거) =================
            String[] headers = {

                    "고유번호",
                    "지점",
                    "제목",
                    "분야야",
                    "상태",
                    "생성일",
                    "제출일",
                    "작업시작일",
                    "작업종료일"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            // ================= 데이터 =================
            int rowIdx = 1;
            int seq = 1;

            for (Maintenance m : rows) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;

                row.createCell(c++).setCellValue(m.getId()); // ✅ DB ID (노출)

                row.createCell(c++).setCellValue(
                        m.getBranch() == null ? "" : safe(m.getBranch().getBranchName()));
                row.createCell(c++).setCellValue(safe(m.getTitle()));
                row.createCell(c++).setCellValue(
                        m.getCategory() == null ? "" : m.getCategory().getDisplayName());
                row.createCell(c++).setCellValue(
                        m.getStatus() == null ? "" : m.getStatus().kr());
                row.createCell(c++).setCellValue(
                        m.getCreatedAt() == null ? "" : m.getCreatedAt().toLocalDate().format(df));
                row.createCell(c++).setCellValue(
                        m.getSubmittedAt() == null ? "" : m.getSubmittedAt().toLocalDate().format(df));
                row.createCell(c++).setCellValue(
                        m.getWorkStartDate() == null ? "" : m.getWorkStartDate().format(df));
                row.createCell(c++).setCellValue(
                        m.getWorkEndDate() == null ? "" : m.getWorkEndDate().format(df));
            }

            // ================= 컬럼 너비 고정 =================
            int[] widths = {
                    18,
                    18, // 지점
                    30, // 제목
                    16, // 카테고리
                    16, // 상태
                    14, // 생성일
                    14, // 제출일
                    14, // 작업시작일
                    14 // 작업종료일
            };
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();

        } catch (Exception e) {
            throw new ExceptionApi400("엑셀 생성 실패: " + e.getMessage());
        }
    }

    // ✅ null-safe string helper (이미 있으면 삭제하고 기존 거 써도 됨)
    private String safe(String s) {
        return s == null ? "" : s;
    }

}
