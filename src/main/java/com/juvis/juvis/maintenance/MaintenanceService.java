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

        if (mr.getStatus() != MaintenanceStatus.DRAFT && mr.getStatus() != MaintenanceStatus.REJECTED) {
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

    // TODO 구현부들은 정책 확정되면 채우기
    @Transactional
    public Maintenance assignVendor(LoginUser loginUser, Long id, MaintenanceRequest.AssignVendorDTO dto) {
        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }
        return null;
    }

    @Transactional
    public Maintenance approve(LoginUser loginUser, Long id, MaintenanceRequest.ApproveDTO dto) {
        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }
        return null;
    }

    @Transactional
    public Maintenance reject(LoginUser loginUser, Long id, MaintenanceRequest.RejectDTO dto) {
        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("HQ 권한이 필요합니다.");
        }
        return null;
    }

    // ========================= VENDOR =========================

    public List<Maintenance> findForVendor(LoginUser loginUser, String status) {

        if (loginUser == null || loginUser.role() != UserRole.VENDOR) {
            throw new ExceptionApi403("VENDOR 권한이 필요합니다.");
        }

        User vendor = loadUser(loginUser);

        if (status != null && !status.isBlank()) {
            MaintenanceStatus s = MaintenanceStatus.valueOf(status.trim().toUpperCase());
            return maintenanceRepository.findByVendorAndStatus(vendor, s);
        }

        return maintenanceRepository.findByVendor(vendor);
    }

    @Transactional
    public Maintenance submitEstimate(LoginUser loginUser, Long id, MaintenanceRequest.SubmitEstimateDTO dto) {
        if (loginUser == null || loginUser.role() != UserRole.VENDOR) {
            throw new ExceptionApi403("VENDOR 권한이 필요합니다.");
        }
        return null;
    }

    @Transactional
    public Maintenance completeWork(LoginUser loginUser, Long id, MaintenanceRequest.CompleteWorkDTO dto) {
        if (loginUser == null || loginUser.role() != UserRole.VENDOR) {
            throw new ExceptionApi403("VENDOR 권한이 필요합니다.");
        }
        return null;
    }
}
