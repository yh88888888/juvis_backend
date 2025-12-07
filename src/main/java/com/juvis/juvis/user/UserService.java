package com.juvis.juvis.user;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi400;
import com.juvis.juvis._core.error.ex.ExceptionApi401;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis._core.util.JwtUtil;
import com.juvis.juvis.branch.Branch;
import com.juvis.juvis.branch.BranchRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Transactional
    public UserResponse.JoinDTO join(UserRequest.JoinDTO reqDTO) {
        userRepository.findByUsername(reqDTO.getUsername()).ifPresent(user -> {
            throw new ExceptionApi400("이미 존재하는 username입니다.");
        });

        String encodedPassword = bCryptPasswordEncoder.encode(reqDTO.getPassword());
        User userPS = userRepository.save(reqDTO.toEntity(encodedPassword));

        log.info("[User Join] id={}, username={} 신규 회원 가입 성공", userPS.getId(), userPS.getUsername());

        return new UserResponse.JoinDTO(userPS);
    }

    public UserResponse.LoginDTO login(UserRequest.LoginDTO loginDTO) {
        User userPS = userRepository.findByUsername(loginDTO.getUsername())
                .orElseThrow(() -> new ExceptionApi401("유저네임 혹은 비밀번호가 틀렸습니다"));

        boolean isSame = bCryptPasswordEncoder.matches(loginDTO.getPassword(), userPS.getPassword());
        if (!isSame)
            throw new ExceptionApi401("유저네임 혹은 비밀번호가 틀렸습니다");

        String accessToken = JwtUtil.create(userPS);

        return new UserResponse.LoginDTO(accessToken, userPS);
    }

    @Transactional
    public UserResponse.JoinDTO joinBranch(UserRequest.BranchJoinDTO reqDTO, User currentUser) {

        // 1) HQ 권한인지 확인
        if (currentUser.getRole() != UserRole.HQ) {
            throw new ExceptionApi403("지점 생성 권한이 없습니다.");
        }

        // 2) username 중복 체크
        userRepository.findByUsername(reqDTO.getUsername()).ifPresent(user -> {
            throw new ExceptionApi400("이미 존재하는 username입니다.");
        });

        // 3) branchName 중복 체크
        branchRepository.findByName(reqDTO.getBranchName()).ifPresent(b -> {
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
                .username(reqDTO.getUsername()) // juvis_hw
                .password(encodedPassword)
                .name(reqDTO.getBranchName()) // 화면표시용: 해운대점
                .phone(reqDTO.getBranchPhone())
                .role(UserRole.BRANCH) // enum 혹은 String
                .branch(savedBranch) // FK
                .build();

        User userPS = userRepository.save(branchUser);

        log.info("[Branch Join] branchName={}, username={} 지점+계정 생성",
                savedBranch.getBranchName(), userPS.getUsername());

        return new UserResponse.JoinDTO(userPS);
    }

}
