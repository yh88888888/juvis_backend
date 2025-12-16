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
import com.juvis.juvis.user.User;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
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
        return Resp.ok(new MaintenanceResponse.DetailDTO(m));
    }

    /**
     * 지점 – DRAFT/REJECTED 상태 요청 제출 (REQUESTED)
     */
    @PostMapping("/api/branch/maintenances/{id}/submit")
    public ResponseEntity<?> submitRequest(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long id) {
        Maintenance m = maintenanceService.submitRequest(loginUser, id);
        return Resp.ok(new MaintenanceResponse.DetailDTO(m));
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
            @PathVariable Long id) {

        Maintenance m = maintenanceService.getDetailForBranch(loginUser, id);
        return Resp.ok(new MaintenanceResponse.DetailDTO(m));
    }

    // ========================= HQ =========================
    // HQ – 전체 요청 목록 조회 (status/category/branchId + 페이징)
    @GetMapping("/hq/maintenance/requests")
    public ResponseEntity<Resp<Page<MaintenanceResponse.SimpleDTO>>> getRequestsForHq(
            @AuthenticationPrincipal LoginUser currentUser,
            @RequestParam(required = false) MaintenanceStatus status,
            @RequestParam(required = false) MaintenanceCategory category,
            @RequestParam(required = false) Long branchId,
            @PageableDefault(page = 0, size = 20) @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<Maintenance> result = maintenanceService.getHqList(currentUser, status, category, branchId, pageable);
        Page<MaintenanceResponse.SimpleDTO> dtoPage = result.map(MaintenanceResponse.SimpleDTO::new);
        return Resp.ok(dtoPage);
    }

    /**
     * HQ – 요청 상세 보기
     */

    @GetMapping("/hq/maintenance/requests/{id}")
    public ResponseEntity<?> getDetailForHq(
            @AuthenticationPrincipal LoginUser currentUser,
            @PathVariable Long id) {
        if (currentUser.role() != UserRole.HQ) {
            return Resp.forbidden("HQ 권한이 필요합니다.");
        }

        Maintenance m = maintenanceService.getDetailForHq(currentUser, id);
        return Resp.ok(new MaintenanceResponse.DetailDTO(m));
    }

    /**
     * HQ – Vendor 지정 (ESTIMATING으로 변경)
     */

    @PostMapping("/hq/maintenance/requests/{id}/assign-vendor")
    public ResponseEntity<?> assignVendor(
            @AuthenticationPrincipal LoginUser currentUser,
            @PathVariable Long id,
            @RequestBody MaintenanceRequest.AssignVendorDTO dto) {
        if (currentUser.role() != UserRole.HQ) {
            return Resp.forbidden("HQ 권한이 필요합니다.");
        }

        Maintenance m = maintenanceService.assignVendor(currentUser, id, dto);
        return Resp.ok(new MaintenanceResponse.DetailDTO(m));
    }

    /**
     * HQ – 승인 (APPROVAL_PENDING → IN_PROGRESS)
     */
    @PostMapping("/hq/maintenance/requests/{id}/approve")
    public ResponseEntity<?> approve(
            @AuthenticationPrincipal LoginUser currentUser,
            @PathVariable Long id,
            @RequestBody(required = false) MaintenanceRequest.ApproveDTO dto) {
        if (currentUser.role() != UserRole.HQ) {
            return Resp.forbidden("HQ 권한이 필요합니다.");
        }

        Maintenance m = maintenanceService.approve(currentUser, id, dto);
        return Resp.ok(new MaintenanceResponse.DetailDTO(m));
    }

    /**
     * HQ – 반려 (REJECTED)
     */

    @PostMapping("/hq/maintenance/requests/{id}/reject")
    public ResponseEntity<?> reject(
            @AuthenticationPrincipal LoginUser currentUser,
            @PathVariable Long id,
            @RequestBody MaintenanceRequest.RejectDTO dto) {
        if (currentUser.role() != UserRole.HQ) {
            return Resp.forbidden("HQ 권한이 필요합니다.");
        }

        Maintenance m = maintenanceService.reject(currentUser, id, dto);
        return Resp.ok(new MaintenanceResponse.DetailDTO(m));
    }

    // ========================= VENDOR =========================

    /**
     * Vendor – 내게 배정된 요청 목록
     */

    @GetMapping("/vendor/maintenance/requests")
    public ResponseEntity<?> getRequestsForVendor(
            @AuthenticationPrincipal LoginUser currentUser,
            @RequestParam(required = false) String status) {
        if (currentUser.role() != UserRole.VENDOR) {
            return Resp.forbidden("VENDOR 권한이 필요합니다.");
        }

        List<Maintenance> list = maintenanceService.findForVendor(currentUser, status);
        List<MaintenanceResponse.SimpleDTO> resp = list.stream().map(MaintenanceResponse.SimpleDTO::new).toList();
        return Resp.ok(resp);
    }

    /**
     * Vendor – 견적 제출 (ESTIMATING → APPROVAL_PENDING)
     */
    @PostMapping("/vendor/maintenance/requests/{id}/submit-estimate")
    public ResponseEntity<?> submitEstimate(
            @AuthenticationPrincipal LoginUser currentUser,
            @PathVariable Long id,
            @RequestBody MaintenanceRequest.SubmitEstimateDTO dto) {
        if (currentUser.role() != UserRole.VENDOR) {
            return Resp.forbidden("VENDOR 권한이 필요합니다.");
        }

        Maintenance m = maintenanceService.submitEstimate(currentUser, id, dto);
        return Resp.ok(new MaintenanceResponse.DetailDTO(m));
    }

    /**
     * Vendor – 현장 조치 완료 + 결과/사진 업로드 (IN_PROGRESS → COMPLETED)
     */
    @PostMapping("/vendor/maintenance/requests/{id}/complete")
    public ResponseEntity<?> completeWork(
            @AuthenticationPrincipal LoginUser currentUser,
            @PathVariable Long id,
            @RequestBody MaintenanceRequest.CompleteWorkDTO dto) {
        if (currentUser.role() != UserRole.VENDOR) {
            return Resp.forbidden("VENDOR 권한이 필요합니다.");
        }

        Maintenance m = maintenanceService.completeWork(currentUser, id, dto);
        return Resp.ok(new MaintenanceResponse.DetailDTO(m));
    }
}
