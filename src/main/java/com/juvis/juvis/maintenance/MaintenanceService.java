package com.juvis.juvis.maintenance;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juvis.juvis._core.enums.MaintenanceCategory;
import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi400;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis.branch.BranchRepository;
import com.juvis.juvis.user.User;
import com.juvis.juvis.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    // ---------- 1) 지점: 생성 (DRAFT / REQUESTED) ----------

    @Transactional
    public Maintenance createByBranch(User currentUser, MaintenanceRequest.CreateDTO dto) {
        if (currentUser.getRole() != UserRole.BRANCH) {
            throw new ExceptionApi403("지점만 요청서를 생성할 수 있습니다.");
        }

        MaintenanceStatus status = dto.isSubmit()
                ? MaintenanceStatus.REQUESTED
                : MaintenanceStatus.DRAFT;

        LocalDateTime submittedAt = dto.isSubmit() ? LocalDateTime.now() : null;

        Maintenance mr = Maintenance.builder()
                .branch(currentUser.getBranch())
                .requester(currentUser)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(status)
                .submittedAt(submittedAt)
                .build();

        return maintenanceRepository.save(mr);
    }

    // ---------- 2) 지점: DRAFT/REJECTED → REQUESTED 제출 ----------

    @Transactional
    public Maintenance submitRequest(User currentUser, Long requestId) {
        Maintenance mr = findByIdOrThrow(requestId);

        // 본인인지 체크
        if (!mr.getRequester().getId().equals(currentUser.getId())) {
            throw new ExceptionApi403("본인 작성 요청만 제출할 수 있습니다.");
        }

        // 가능한 상태인지 체크
        if (mr.getStatus() != MaintenanceStatus.DRAFT &&
                mr.getStatus() != MaintenanceStatus.REJECTED) {
            throw new ExceptionApi400("제출할 수 없는 상태입니다.");
        }

        mr.setStatus(MaintenanceStatus.REQUESTED);
        mr.setSubmittedAt(LocalDateTime.now());

        // 변경감지로 자동 반영, save 안 해도 되지만 명시적으로 하고 싶으면:
        return mr;
    }

    // ---------- 3) 지점: 내가 요청한 목록 ----------

    public List<Maintenance> findMyRequests(User currentUser) {
        return maintenanceRepository.findByRequester(currentUser);
    }

    // ---------- 4) 지점: 상세 (내 요청/내 지점 것만 허용) ----------

    public Maintenance getDetailForBranch(User currentUser, Long id) {
        Maintenance mr = findByIdOrThrow(id);

        // 내 요청이거나, 내 지점의 요청인지 확인 (필요에 따라)
        if (!mr.getRequester().getId().equals(currentUser.getId())
                && !mr.getBranch().getId().equals(currentUser.getBranch().getId())) {
            throw new ExceptionApi403("열람 권한이 없습니다.");
        }

        return mr;
    }

    // ---------- 5) HQ: 목록 / 상세 ----------

    public List<Maintenance> findForHq(String statusStr, String categoryStr, Long branchId) {
        MaintenanceStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            status = MaintenanceStatus.valueOf(statusStr);
        }

        MaintenanceCategory category = null;
        if (categoryStr != null && !categoryStr.isBlank()) {
            category = MaintenanceCategory.valueOf(categoryStr);
        }

        return maintenanceRepository.searchForHq(status, category, branchId);
    }

    public Maintenance getDetailForHq(User currentUser, Long id) {
        // currentUser가 HQ인지 체크는 컨트롤러에서 이미 했다고 가정
        return findByIdOrThrow(id);
    }

    // ---------- 6) HQ: 벤더 지정 / 승인 / 반려 (이 부분은 나중에 채우면 됨) ----------

    @Transactional
    public Maintenance assignVendor(User currentUser, Long id, MaintenanceRequest.AssignVendorDTO dto) {
        // 나중에 구현
        return null;
    }

    @Transactional
    public Maintenance approve(User currentUser, Long id, MaintenanceRequest.ApproveDTO dto) {
        // 나중에 구현
        return null;
    }

    @Transactional
    public Maintenance reject(User currentUser, Long id, MaintenanceRequest.RejectDTO dto) {
        // 나중에 구현
        return null;
    }

    // ---------- 7) Vendor: 목록 / 견적 제출 / 완료 ----------

    public List<Maintenance> findForVendor(User currentUser, String status) {
        if (status != null) {
            MaintenanceStatus s = MaintenanceStatus.valueOf(status);
            return maintenanceRepository.findByVendorAndStatus(currentUser, s);
        }
        return maintenanceRepository.findByVendor(currentUser);
    }

    @Transactional
    public Maintenance submitEstimate(User currentUser, Long id, MaintenanceRequest.SubmitEstimateDTO dto) {
        // 나중에 구현
        return null;
    }

    @Transactional
    public Maintenance completeWork(User currentUser, Long id, MaintenanceRequest.CompleteWorkDTO dto) {
        // 나중에 구현
        return null;
    }

    // ---------- 공통 헬퍼 ----------

    private Maintenance findByIdOrThrow(Long id) {
        return maintenanceRepository.findById(id)
                .orElseThrow(() -> new ExceptionApi400("요청을 찾을 수 없습니다. id=" + id));
    }
}
