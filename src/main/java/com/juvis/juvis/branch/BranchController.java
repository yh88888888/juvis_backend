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

    public record BranchSimpleDTO(Long id, String branchName) {
        public static BranchSimpleDTO from(Branch b) {
            return new BranchSimpleDTO(b.getId(), b.getBranchName());
        }
    }

    @GetMapping("/api/hq/branches")
    public ResponseEntity<?> getBranchesForHq(
            @AuthenticationPrincipal LoginUser loginUser) {
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
