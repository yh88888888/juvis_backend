package com.juvis.juvis.maintenance;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.util.Resp;
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
    @PostMapping("/maintenance/requests")
    public ResponseEntity<?> createRequest(
            @AuthenticationPrincipal User currentUser,
            @RequestBody MaintenanceRequest.CreateDTO dto) {
        Maintenance m = maintenanceService.createByBranch(currentUser, dto);
        return Resp.ok(new MaintenanceResponse.DetailDTO(m));
    }

    /**
     * 지점 – DRAFT/REJECTED 상태 요청 제출 (REQUESTED)
     */
    @PostMapping("/maintenance/requests/{id}/submit")
    public ResponseEntity<?> submitRequest(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        Maintenance m = maintenanceService.submitRequest(currentUser, id);
        return Resp.ok(new MaintenanceResponse.DetailDTO(m));
    }

    /**
     * 지점 – 내가 작성한 요청 목록
     */
    @GetMapping("/maintenance/requests/my")
    public ResponseEntity<?> getMyRequests(
            @AuthenticationPrincipal User currentUser) {
        List<Maintenance> list = maintenanceService.findMyRequests(currentUser);
        List<MaintenanceResponse.SimpleDTO> resp = list.stream().map(MaintenanceResponse.SimpleDTO::new).toList();
        return Resp.ok(resp);
    }

    /**
     * 지점 – 특정 요청 상세(내가 쓴 것 또는 내 지점 것만 허용)
     */
    @GetMapping("/maintenance/requests/{id}")
    public ResponseEntity<?> getDetailForBranch(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        Maintenance m = maintenanceService.getDetailForBranch(currentUser, id);
        return Resp.ok(new MaintenanceResponse.DetailDTO(m));
    }

    // ========================= HQ =========================

    /**
     * HQ – 전체 요청 목록 조회 (status / branchId 필터링 가능)
     */
    @GetMapping("/hq/maintenance/requests")
    public ResponseEntity<?> getRequestsForHq(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long branchId) {
        if (currentUser.getRole() != UserRole.HQ) {
            return Resp.forbidden("HQ 권한이 필요합니다.");
        }

        List<Maintenance> list = maintenanceService.findForHq(status, category, branchId);
        List<MaintenanceResponse.SimpleDTO> resp = list.stream().map(MaintenanceResponse.SimpleDTO::new).toList();
        return Resp.ok(resp);
    }

    /**
     * HQ – 요청 상세 보기
     */
    @GetMapping("/hq/maintenance/requests/{id}")
    public ResponseEntity<?> getDetailForHq(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        if (currentUser.getRole() != UserRole.HQ) {
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
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody MaintenanceRequest.AssignVendorDTO dto) {
        if (currentUser.getRole() != UserRole.HQ) {
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
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody(required = false) MaintenanceRequest.ApproveDTO dto) {
        if (currentUser.getRole() != UserRole.HQ) {
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
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody MaintenanceRequest.RejectDTO dto) {
        if (currentUser.getRole() != UserRole.HQ) {
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
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String status) {
        if (currentUser.getRole() != UserRole.VENDOR) {
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
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody MaintenanceRequest.SubmitEstimateDTO dto) {
        if (currentUser.getRole() != UserRole.VENDOR) {
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
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody MaintenanceRequest.CompleteWorkDTO dto) {
        if (currentUser.getRole() != UserRole.VENDOR) {
            return Resp.forbidden("VENDOR 권한이 필요합니다.");
        }

        Maintenance m = maintenanceService.completeWork(currentUser, id, dto);
        return Resp.ok(new MaintenanceResponse.DetailDTO(m));
    }
}
