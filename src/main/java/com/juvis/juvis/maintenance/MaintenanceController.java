package com.juvis.juvis.maintenance;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.juvis.juvis._core.enums.MaintenanceCategory;
import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.util.Resp;
import com.juvis.juvis.user.LoginUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@RestController
@Slf4j
public class MaintenanceController {
    private final MaintenanceService maintenanceService;

    // ========================= BRANCH =========================

    /**
     * 지점 – 유지보수 요청 생성 (DRAFT 또는 REQUESTED)
     */
    @PostMapping("/api/branch/maintenances")
    public ResponseEntity<?> createRequest(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody MaintenanceRequest.CreateDTO dto) {
        Maintenance m = maintenanceService.createByBranch(loginUser, dto);
        return Resp.ok(maintenanceService.toDetailDTO(m));
    }

    /**
     * 지점 – DRAFT/REJECTED 상태 요청 제출 (REQUESTED)
     */
    @PostMapping("/api/branch/maintenances/{id}/submit")
    public ResponseEntity<?> submitRequest(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id) {
        maintenanceService.submitRequest(loginUser, id);
        return Resp.ok("제출완료");
    }

    @GetMapping("/api/branch/maintenances")
    public ResponseEntity<Resp<Page<MaintenanceResponse.SimpleDTO>>> getBranchList(
            @AuthenticationPrincipal LoginUser loginUser,

            @RequestParam(name = "status", required = false) MaintenanceStatus status,

            @RequestParam(name = "category", required = false) MaintenanceCategory category,

            @PageableDefault(page = 0, size = 20) @SortDefault.SortDefaults({
                    @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            }) Pageable pageable) {
        Page<Maintenance> result = maintenanceService.getBranchList(loginUser, status, category, pageable);
        return Resp.ok(result.map(MaintenanceResponse.SimpleDTO::new));
    }

    /**
     * 지점 – 특정 요청 상세(내가 쓴 것 또는 내 지점 것만 허용)
     */
    @GetMapping("/api/branch/maintenances/{id}")
    public ResponseEntity<Resp<MaintenanceResponse.DetailDTO>> getDetailForBranch(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") Long id) {

        MaintenanceResponse.DetailDTO dto = maintenanceService.getDetailForBranch(loginUser, id);

        return Resp.ok(dto);
    }

    // ========================= HQ =========================
    // HQ – 전체 요청 목록 조회 (status/category/branchId + 페이징)
    @GetMapping("/hq/maintenance/requests")
    public ResponseEntity<Resp<Page<MaintenanceResponse.SimpleDTO>>> getRequestsForHq(
            @AuthenticationPrincipal LoginUser currentUser,
            @RequestParam(name = "status", required = false) MaintenanceStatus status,
            @RequestParam(name = "category", required = false) MaintenanceCategory category,
            @RequestParam(name = "branchId", required = false) Long branchId,
            @PageableDefault(page = 0, size = 20) @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Maintenance> result = maintenanceService.getHqList(currentUser, status, category, branchId, pageable);
        Page<MaintenanceResponse.SimpleDTO> dtoPage = result.map(MaintenanceResponse.SimpleDTO::new);
        return Resp.ok(dtoPage);
    }

    @GetMapping("/api/hq/maintenance/requests/{id}")
    public ResponseEntity<?> getDetailForHq(
            @AuthenticationPrincipal LoginUser currentUser,
            @PathVariable("id") Long id) {

        MaintenanceResponse.DetailDTO dto = maintenanceService.getDetailDtoForHq(currentUser, id);
        return Resp.ok(dto);
    }

    // ✅ HQ 1차 승인: REQUESTED -> ESTIMATING
    @PostMapping("/api/hq/maintenance/requests/{id}/approve-request")
    public ResponseEntity<?> approveRequest(
            @AuthenticationPrincipal LoginUser currentUser,
            @PathVariable("id") Long id,
            @RequestBody(required = false) MaintenanceRequest.ApproveDTO dto) {

        if (currentUser.role() != UserRole.HQ) {
            return Resp.forbidden("HQ 권한이 필요합니다.");
        }

        maintenanceService.approveRequest(currentUser, id, dto);

        // ✅ 변경 후 최신 상세 조회
        Maintenance m = maintenanceService.findDetailOrThrow(id);
        return Resp.ok(maintenanceService.toDetailDTO(m));
    }

    // ✅ HQ 2차 승인: APPROVAL_PENDING -> IN_PROGRESS
    @PostMapping("/api/hq/maintenance/requests/{id}/approve-estimate")
    public ResponseEntity<?> approveEstimate(
            @AuthenticationPrincipal LoginUser currentUser,
            @PathVariable("id") Long id) {

        if (currentUser.role() != UserRole.HQ) {
            return Resp.forbidden("HQ 권한이 필요합니다.");
        }

        maintenanceService.approveEstimate(id, currentUser);

        // ✅ 변경 후 최신 상세 조회
        Maintenance m = maintenanceService.findDetailOrThrow(id);
        return Resp.ok(maintenanceService.toDetailDTO(m));
    }

    // ✅ HQ 1차 반려: REQUESTED -> HQ1_REJECTED
    @PostMapping("/api/hq/maintenance/requests/{id}/reject-request")
    public ResponseEntity<?> rejectRequest(
            @AuthenticationPrincipal LoginUser currentUser,
            @PathVariable("id") Long id,
            @RequestBody MaintenanceRequest.RejectDTO dto) {

        if (currentUser.role() != UserRole.HQ) {
            return Resp.forbidden("HQ 권한이 필요합니다.");
        }

        maintenanceService.rejectRequest(currentUser, id, dto);

        // ✅ 변경 후 최신 상세 조회
        Maintenance m = maintenanceService.findDetailOrThrow(id);
        return Resp.ok(maintenanceService.toDetailDTO(m));
    }

    // ✅ HQ 2차 반려: APPROVAL_PENDING -> HQ2_REJECTED / ESTIMATE_FINAL_REJECTED
    @PostMapping("/api/hq/maintenance/requests/{id}/reject-estimate")
    public ResponseEntity<?> rejectEstimate(
            @AuthenticationPrincipal LoginUser currentUser,
            @PathVariable("id") Long id,
            @RequestBody MaintenanceRequest.RejectDTO dto) {

        if (currentUser.role() != UserRole.HQ) {
            return Resp.forbidden("HQ 권한이 필요합니다.");
        }

        maintenanceService.rejectEstimate(id, currentUser, dto.getReason());

        // ✅ 변경 후 최신 상세 조회
        Maintenance m = maintenanceService.findDetailOrThrow(id);
        return Resp.ok(maintenanceService.toDetailDTO(m));
    }
    // ========================= VENDOR =========================

    @GetMapping("/api/vendor/maintenance/requests")
    public ResponseEntity<?> getRequestsForVendor(
            @AuthenticationPrincipal LoginUser currentUser,
            @RequestParam(name = "status", required = false) String status) {

        if (currentUser.role() != UserRole.VENDOR) {
            return Resp.forbidden("VENDOR 권한이 필요합니다.");
        }

        List<Maintenance> list = maintenanceService.findForVendor(currentUser, status);
        List<MaintenanceResponse.SimpleDTO> resp = list.stream().map(MaintenanceResponse.SimpleDTO::new).toList();
        return Resp.ok(resp);
    }

    @GetMapping("/api/vendor/maintenance/requests/{id}")
    public ResponseEntity<?> getDetailForVendor(
            @AuthenticationPrincipal LoginUser currentUser,
            @PathVariable("id") Long id) {

        if (currentUser.role() != UserRole.VENDOR) {
            return Resp.forbidden("VENDOR 권한이 필요합니다.");
        }

        log.info("[SUBMIT_ESTIMATE] pathId={}", id);
        Maintenance m = maintenanceService.findDetailOrThrow(id);
        log.info("[SUBMIT_ESTIMATE] maintenance.id={} status={}", m.getId(), m.getStatus());
        return Resp.ok(maintenanceService.toDetailDTO(m));
    }

    @PostMapping("/api/vendor/maintenance/requests/{id}/submit-estimate")
    public ResponseEntity<?> submitEstimate(
            @AuthenticationPrincipal LoginUser currentUser,
            @PathVariable("id") Long id,
            @RequestBody MaintenanceRequest.SubmitEstimateDTO dto) {

        if (currentUser.role() != UserRole.VENDOR) {
            return Resp.forbidden("VENDOR 권한이 필요합니다.");
        }

        maintenanceService.submitEstimate(currentUser, id, dto);

        // ✅ 상세 DTO 만들지 말고 단순 OK 반환
        return Resp.ok("OK");
    }

    @PostMapping("/api/vendor/maintenance/requests/{id}/complete")
public ResponseEntity<?> completeWork(
        @AuthenticationPrincipal LoginUser currentUser,
        @PathVariable("id") Long id,
        @RequestBody MaintenanceRequest.CompleteWorkDTO dto) {

    if (currentUser.role() != UserRole.VENDOR) {
        return Resp.forbidden("VENDOR 권한이 필요합니다.");
    }

    return Resp.ok(maintenanceService.completeWorkAndGetDetail(currentUser, id, dto));
}
}
