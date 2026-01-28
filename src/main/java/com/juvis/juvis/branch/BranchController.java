package com.juvis.juvis.branch;

import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.util.Resp;
import com.juvis.juvis.user.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BranchController {

    private final BranchRepository branchRepository;

    // ✅ 프론트(BranchItem.fromJson)에서 addressName 읽으니까 같이 내려주기
    public record BranchSimpleDTO(Long id, String branchName, String addressName) {
        public static BranchSimpleDTO from(Branch b) {
            return new BranchSimpleDTO(
                    b.getId(),
                    b.getBranchName(),
                    b.getAddressName() // ⚠️ getAddress() 아님!
            );
        }
    }

    // ========================= OPS (HQ + VENDOR) =========================
    // ✅ 공용: 전체 지점 목록
    @GetMapping("/api/ops/branches")
    public ResponseEntity<?> getBranchesForOps(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null || !(loginUser.role() == UserRole.HQ || loginUser.role() == UserRole.VENDOR)) {
            return Resp.forbidden("HQ 또는 VENDOR 권한이 필요합니다.");
        }

        List<BranchSimpleDTO> list = branchRepository.findAllOrderByName()
                .stream()
                .map(BranchSimpleDTO::from)
                .toList();

        return Resp.ok(list);
    }

    // ========================= HQ (기존 유지, 필요 없으면 삭제 가능) =========================
    @GetMapping("/api/hq/branches")
    public ResponseEntity<?> getBranchesForHq(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            return Resp.forbidden("HQ 권한이 필요합니다.");
        }

        List<BranchSimpleDTO> list = branchRepository.findAllOrderByName()
                .stream()
                .map(BranchSimpleDTO::from)
                .toList();

        return Resp.ok(list);
    }
}
