package com.juvis.juvis.user;

import lombok.Data;

public class UserResponse {
    @Data
    public static class JoinDTO {
        private Integer userId;
        private String username;
        private String role;

        public JoinDTO(User user) {
            this.userId = user.getId();
            this.username = user.getUsername();
            this.role = user.getRole().name();
        }
    }

    @Data
    public static class LoginDTO {
        private Integer id;
        private String accessToken;
        private String username;
        private String role;
        private String name;

        public LoginDTO(String accessToken, User user) {
            this.id = user.getId();
            this.accessToken = accessToken;
            this.username = user.getUsername();
            this.role = user.getRole().name();
            this.name = user.getName();
        }
    }

}
