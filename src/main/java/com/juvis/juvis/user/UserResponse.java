package com.juvis.juvis.user;

import com.juvis.juvis._core.enums.UserRole;

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
    }

    @Data
    public static class LoginDTO {
        private Integer id;
        private String accessToken;
        private String refreshToken;
        private String username;
        private String role;
        private String name;

        public LoginDTO(String accessToken, String refreshToken, User user) {
            this.id = user.getId();
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.username = user.getUsername();
            this.role = user.getRole().name();
            if (user.getRole() == UserRole.BRANCH && user.getBranch() != null) {
                this.name = user.getBranch().getBranchName();
            }
        }
    }

}
