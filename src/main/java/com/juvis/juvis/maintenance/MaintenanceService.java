package com.juvis.juvis.maintenance;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juvis.juvis._core.enums.MaintenanceCategory;
import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi400;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis.branch.Branch;
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

    @Transactional
    public Maintenance createByBranch(User currentUser, MaintenanceRequest.CreateDTO dto) {
        if (currentUser.getRole() != UserRole.BRANCH) {
            throw new ExceptionApi403("지점만 요청서를 생성할 수 있습니다.");
        }

        Branch branch = currentUser.getBranch();

        // 2) 해당 지점의 다음 branch_seq 계산
        Integer maxSeq = maintenanceRepository.findMaxBranchSeqByBranchId(branch.getId());
        int nextSeq = (maxSeq == null || maxSeq == 0) ? 1 : maxSeq + 1;

        MaintenanceStatus status = dto.isSubmit() ? MaintenanceStatus.REQUESTED : MaintenanceStatus.DRAFT;
        LocalDateTime submittedAt = dto.isSubmit() ? LocalDateTime.now() : null;

        Maintenance mr = Maintenance.builder()
                .branch(branch)
                .branchSeq(nextSeq) // ✅ 지점별 순번
                .requester(currentUser)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(status)
                .category(dto.getCategory()) // DTO에 category 들어왔다고 가정
                .submittedAt(submittedAt)
                .build();

        return maintenanceRepository.save(mr);
    }

    @Transactional
    public Maintenance submitRequest(User currentUser, Long requestId) {
        Maintenance mr = findByIdOrThrow(requestId);
        if (!mr.getRequester().getId().equals(currentUser.getId())) {
            throw new ExceptionApi403("본인 작성 요청만 제출할 수 있습니다.");
        }
        if (mr.getStatus() != MaintenanceStatus.DRAFT && mr.getStatus() != MaintenanceStatus.REJECTED) {
            throw new ExceptionApi400("제출할 수 없는 상태입니다.");
        }
        mr.setStatus(MaintenanceStatus.REQUESTED);
        mr.setSubmittedAt(LocalDateTime.now());
        return mr; // dirty checking
    }

    public Maintenance getDetailForBranch(User currentUser, Long id) {
        Maintenance mr = findByIdOrThrow(id);
        if (!mr.getRequester().getId().equals(currentUser.getId())
                && !mr.getBranch().getId().equals(currentUser.getBranch().getId())) {
            throw new ExceptionApi403("열람 권한이 없습니다.");
        }
        return mr;
    }

    // HQ 목록(Page)
    public Page<Maintenance> getHqList(MaintenanceStatus status,
            MaintenanceCategory category,
            Long branchId,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return maintenanceRepository.searchForHq(status, category, branchId, pageable);
    }

    // Branch 목록(Page)
    public Page<Maintenance> getBranchList(User currentUser,
            MaintenanceStatus status,
            MaintenanceCategory category,
            int page,
            int size) {

        if (currentUser.getBranch() == null) {
            throw new ExceptionApi403("지점 계정만 조회할 수 있습니다.");
        }
        Long branchId = currentUser.getBranch().getId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return maintenanceRepository.searchForBranch(branchId, status, category, pageable);
    }

    public Maintenance getDetailForHq(User currentUser, Long id) {
        return findByIdOrThrow(id);
    }

    @Transactional
    public Maintenance assignVendor(User currentUser, Long id, MaintenanceRequest.AssignVendorDTO dto) {
        // TODO: 구현
        return null;
    }

    @Transactional
    public Maintenance approve(User currentUser, Long id, MaintenanceRequest.ApproveDTO dto) {
        // TODO: 구현
        return null;
    }

    @Transactional
    public Maintenance reject(User currentUser, Long id, MaintenanceRequest.RejectDTO dto) {
        // TODO: 구현
        return null;
    }

    public List<Maintenance> findForVendor(User currentUser, String status) {
        if (status != null && !status.isBlank()) {
            MaintenanceStatus s = MaintenanceStatus.valueOf(status);
            return maintenanceRepository.findByVendorAndStatus(currentUser, s);
        }
        return maintenanceRepository.findByVendor(currentUser);
    }

    @Transactional
    public Maintenance submitEstimate(User currentUser, Long id, MaintenanceRequest.SubmitEstimateDTO dto) {
        // TODO: 구현
        return null;
    }

    @Transactional
    public Maintenance completeWork(User currentUser, Long id, MaintenanceRequest.CompleteWorkDTO dto) {
        // TODO: 구현
        return null;
    }

    private Maintenance findByIdOrThrow(Long id) {
        return maintenanceRepository.findById(id)
                .orElseThrow(() -> new ExceptionApi400("요청을 찾을 수 없습니다. id=" + id));
    }
}