package com.juvis.juvis.user;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UserRequest {

    @Data
    public static class JoinDTO {
        @Email( message = "이메일 형식이 올바르지 않습니다")
        @NotBlank(message = "이메일은 필수 입력 값입니다")
        private String email;

        @Pattern(
                regexp = "^(?!.*\\.\\.)(?!\\.)(?!.*\\.$)[a-z0-9._]{2,20}$",
                message = "아이디는 영문, 숫자, 밑줄(_), 마침표(.)만 사용 가능합니다. (2~20자)"
        )
        @NotBlank(message = "아이디는 필수 입력 값입니다")
        private String username;

        @Size(min = 4, max = 20, message = "비밀번호는 4~20자여야 합니다")
        @NotBlank(message = "비밀번호는 필수 입력 값입니다")
        private String password;

        public User toEntity(String encodedPassword) {
            return User.builder()
                    .email(email)
                    .username(username)
                    .roles("USER")
                    .password(encodedPassword)
                    .build();
        }
    }

    @Data
    public static class LoginDTO {

        @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다")
        @Pattern(
            regexp = "^[a-zA-Z0-9_]+$",
            message = "아이디는 영문, 숫자, 밑줄(_)만 사용할 수 있습니다")
        @NotBlank(message = "아이디는는 필수 입력 값입니다")
        private String username;

        @Size(min = 4, max = 20, message = "비밀번호는 4~20자여야 합니다")
        @NotBlank(message = "비밀번호는 필수 입력 값입니다")
        private String password;
    }
}
