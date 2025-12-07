package com.juvis.juvis.user;

import lombok.Data;

import com.juvis.juvis._core.enums.UserRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UserRequest {

    @Data
    public static class JoinDTO {
        @Pattern(regexp = "^(?!.*\\.\\.)(?!\\.)(?!.*\\.$)[a-z0-9._]{2,20}$", message = "아이디는 영문, 숫자, 밑줄(_), 마침표(.)만 사용 가능합니다. (2~20자)")
        @NotBlank(message = "아이디는 필수 입력 값입니다")
        private String username;

        @Size(min = 4, max = 20, message = "비밀번호는 4~20자여야 합니다")
        @NotBlank(message = "비밀번호는 필수 입력 값입니다")
        private String password;

        public User toEntity(String encodedPassword) {
            return User.builder()
                    .username(username)
                    .role(UserRole.BRANCH)
                    .password(encodedPassword)
                    .build();
        }
    }

    @Data
    public static class LoginDTO {

        @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "아이디는 영문, 숫자, 밑줄(_)만 사용할 수 있습니다")
        @NotBlank(message = "아이디는는 필수 입력 값입니다")
        private String username;

        @Size(min = 4, max = 20, message = "비밀번호는 4~20자여야 합니다")
        @NotBlank(message = "비밀번호는 필수 입력 값입니다")
        private String password;
    }

    @Data
    public static class BranchJoinDTO {

        // 지점 계정 정보
        @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "아이디는 영문, 숫자, 밑줄(_)만 사용할 수 있습니다")
        @NotBlank(message = "아이디는는 필수 입력 값입니다")
        private String username; // 예: juvis_hw

        @Size(min = 4, max = 20, message = "비밀번호는 4~20자여야 합니다")
        @NotBlank(message = "비밀번호는 필수 입력 값입니다")
        private String password; // 초기 비밀번호

        // 지점 정보
        @NotBlank
        private String branchName; // 예: 해운대점

        @NotBlank
        private String branchPhone; // 예: 000-0000-0000

        @NotBlank
        private String branchAddress; // 예: 부산 해운대구 ...

    }
}
