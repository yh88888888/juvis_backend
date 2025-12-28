package com.juvis.juvis.user;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

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

    @Getter
    @Setter
    public static class BranchJoinDTO {

        // user_tb.username
        @NotBlank(message = "username은 필수입니다.")
        private String username;

        // user_tb.password (원문 -> 서비스에서 BCrypt)
        @NotBlank(message = "password는 필수입니다.")
        private String password;

        // branch.branch_name
        @NotBlank(message = "branchName은 필수입니다.")
        private String branchName;

        // branch.phone + user_tb.phone
        @NotBlank(message = "branchPhone은 필수입니다.")
        private String branchPhone;

        // branch.address_name
        @NotBlank(message = "branchAddress는 필수입니다.")
        private String branchAddress;
    }

}
