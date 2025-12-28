package com.juvis.juvis.user;

import java.util.List;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi400;
import com.juvis.juvis._core.error.ex.ExceptionApi401;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis._core.error.ex.ExceptionApi404;
import com.juvis.juvis._core.util.JwtUtil;
import com.juvis.juvis.branch.Branch;
import com.juvis.juvis.branch.BranchRepository;
import com.juvis.juvis.user.UserResponse.BranchUserItemDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Transactional(noRollbackFor = { ExceptionApi401.class, ExceptionApi403.class })
    public UserResponse.LoginDTO login(UserRequest.LoginDTO loginDTO) {

        User userPS = userRepository.findByUsername(loginDTO.getUsername())
                .orElseThrow(() -> new ExceptionApi401("유저네임 혹은 비밀번호가 틀렸습니다"));

        // ✅ 0) 영구 잠금 계정 차단 (최우선)
        if (userPS.isAccountLocked()) {
            throw new ExceptionApi403("비밀번호 5회 오류로 계정이 잠금 처리되었습니다. 관리자에게 초기화를 요청하세요.");
        }

        // ✅ 1) 비활성 계정 차단
        if (!userPS.isEnabled()) {
            throw new ExceptionApi403("비활성화된 계정입니다. 본사에 문의하세요.");
        }

        // ✅ 2) 비밀번호 검증
        boolean isSame = bCryptPasswordEncoder.matches(loginDTO.getPassword(), userPS.getPassword());
        if (!isSame) {
            // 실패 카운트 증가
            int nextFail = userPS.getLoginFailCount() + 1;
            userPS.setLoginFailCount(nextFail);

            // 5회 이상이면 영구 잠금
            if (nextFail >= 5) {
                userPS.setAccountLocked(true);
                userRepository.save(userPS);

                throw new ExceptionApi403("비밀번호 5회 오류로 계정이 잠금(영구) 처리되었습니다. 관리자에게 초기화를 요청하세요.");
            }

            userRepository.save(userPS);

            // 남은 횟수 안내(원하면 메시지 유지해도 됨)
            int left = 5 - nextFail;
            throw new ExceptionApi401("유저네임 혹은 비밀번호가 틀렸습니다. 남은 시도 횟수: " + left);
        }

        // ✅ 3) 성공 시 실패 카운트 초기화 (잠금은 해제하지 않음)
        if (userPS.getLoginFailCount() != 0) {
            userPS.setLoginFailCount(0);
            userRepository.save(userPS);
        }

        String accessToken = JwtUtil.createAccessToken(userPS);
        String refreshToken = JwtUtil.createRefreshToken(userPS);

        return new UserResponse.LoginDTO(accessToken, refreshToken, userPS);
    }

    @Transactional
    public UserResponse.JoinDTO joinBranch(UserRequest.BranchJoinDTO reqDTO, LoginUser loginUser) {

        if (loginUser == null) {
            throw new ExceptionApi401("인증 필요");
        }

        if (loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("지점 생성 권한이 없습니다.");
        }

        // 2) username 중복 체크
        userRepository.findByUsername(reqDTO.getUsername()).ifPresent(user -> {
            throw new ExceptionApi400("이미 존재하는 username입니다.");
        });

        // 3) branchName 중복 체크
        branchRepository.findByBranchName(reqDTO.getBranchName()).ifPresent(b -> {
            throw new ExceptionApi400("이미 존재하는 지점명입니다.");
        });

        // 4) branch 생성
        Branch branch = Branch.builder()
                .branchName(reqDTO.getBranchName())
                .phone(reqDTO.getBranchPhone())
                .addressName(reqDTO.getBranchAddress())
                .build();

        Branch savedBranch = branchRepository.save(branch);

        // 5) user(BRANCH 계정) 생성
        String encodedPassword = bCryptPasswordEncoder.encode(reqDTO.getPassword());

        User branchUser = User.builder()
                .username(reqDTO.getUsername())
                .password(encodedPassword)
                .name(reqDTO.getBranchName())
                .phone(reqDTO.getBranchPhone())
                .role(UserRole.BRANCH)
                .branch(savedBranch)
                .active(true) // ✅ 혹시 모를 방어
                .build();

        User userPS = userRepository.save(branchUser);

        log.info("[HQ Branch Create] branchName={}, username={} by hqId={}",
                savedBranch.getBranchName(), userPS.getUsername(), loginUser.id());

        return new UserResponse.JoinDTO(userPS);
    }

    @Transactional(readOnly = true)
    public List<BranchUserItemDTO> getBranchUsers(LoginUser loginUser) {

        if (loginUser == null) {
            throw new ExceptionApi401("인증 필요");
        }

        if (loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("본사 권한 필요");
        }

        return userRepository.findBranchUsers()
                .stream()
                .map(BranchUserItemDTO::from)
                .toList();
    }

    // (다른 메서드들…)

    @Transactional
    public void updateActive(Integer userId, boolean active, LoginUser loginUser) {

        if (loginUser == null)
            throw new ExceptionApi401("인증 필요");

        if (loginUser.role() != UserRole.HQ)
            throw new ExceptionApi403("본사 권한 필요");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ExceptionApi404("사용자 없음"));

        user.setActive(active);
    }

    @Transactional
    public void changeMyPassword(Integer userId, String currentPassword, String newPassword) {
        User userPS = userRepository.findById(userId)
                .orElseThrow(() -> new ExceptionApi404("사용자를 찾을 수 없습니다."));

        // 강제변경 화면에서는 currentPassword를 안받고 싶으면 여기 조건 분기 가능
        if (userPS.isMustChangePassword()) {
            // 초기 상태(강제 변경)면 currentPassword 체크를 생략해도 되고
            // 보수적으로는 체크 유지해도 됨 (아래 주석 해제)
        } else {
            if (currentPassword == null || currentPassword.isBlank()) {
                throw new ExceptionApi400("현재 비밀번호를 입력하세요.");
            }
            boolean ok = bCryptPasswordEncoder.matches(currentPassword, userPS.getPassword());
            if (!ok)
                throw new ExceptionApi401("현재 비밀번호가 올바르지 않습니다.");
        }

        userPS.setPassword(bCryptPasswordEncoder.encode(newPassword));
        userPS.setMustChangePassword(false); // ✅ 변경 완료
    }

    @Transactional
    public void resetPasswordToDefault(Integer targetUserId, LoginUser loginUser) {
        // ✅ HQ만
        if (loginUser == null || loginUser.role() != UserRole.HQ) {
            throw new ExceptionApi403("본사 권한이 필요합니다.");
        }

        User userPS = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ExceptionApi404("사용자를 찾을 수 없습니다."));

        // ✅ 1) 비밀번호 초기화
        userPS.setPassword(bCryptPasswordEncoder.encode("1234"));
        userPS.setMustChangePassword(true); // 다음 로그인 때 강제 변경

        // ✅ 2) 로그인 실패 카운트 초기화
        userPS.setLoginFailCount(0);

        // ✅ 3) 영구 잠금 해제
        userPS.setAccountLocked(false);
    }

}
