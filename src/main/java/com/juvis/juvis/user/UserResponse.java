package com.juvis.juvis.user;

import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis.branch.Branch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

public class UserResponse {
    @Data
    public static class JoinDTO {
        private Integer id;
        private String username;
        private String role;

        public JoinDTO(User user) {
            this.id = user.getId();
            this.username = user.getUsername();
            this.role = user.getRole().name();
        }
    }

    @Getter
    @AllArgsConstructor
    public static class MeDTO {
        private Integer id;
        private String username;
        private String name;
        private String role;
        private boolean mustChangePassword; // ✅ 추가
    }

    @Data
    public static class LoginDTO {
        private Integer id;
        private String accessToken;
        private String refreshToken;
        private String username;
        private String role;
        private String name;

        // ✅ 추가
        private boolean mustChangePassword;

        public LoginDTO(String accessToken, String refreshToken, User user) {
            this.id = user.getId();
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.username = user.getUsername();
            this.role = user.getRole().name();

            if (user.getRole() == UserRole.BRANCH && user.getBranch() != null) {
                this.name = user.getBranch().getBranchName();
            }

            // ✅ 강제 변경 플래그 내려주기
            this.mustChangePassword = user.isMustChangePassword();
        }
    }

    @Getter
    @AllArgsConstructor
    public static class BranchUserItemDTO { // ✅ static 추가
        private Integer userId;
        private String username;

        private Long branchId;
        private String branchName;
        private String branchPhone;
        private String branchAddressName;

        private boolean active;

        public static BranchUserItemDTO from(User u) {
            Branch b = u.getBranch();
            return new BranchUserItemDTO(
                    u.getId(),
                    u.getUsername(),
                    b == null ? null : b.getId(),
                    b == null ? null : b.getBranchName(),
                    b == null ? null : b.getPhone(),
                    b == null ? null : b.getAddressName(),
                    u.isEnabled());
        }
    }

}
