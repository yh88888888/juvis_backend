package com.juvis.juvis.user;

import org.springframework.stereotype.Component;

import com.juvis.juvis._core.error.ex.ExceptionApi401;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CurrentUserLoader {

    private final UserRepository userRepository;

    public User load(LoginUser loginUser) {
        return userRepository.findById(loginUser.id())
                .orElseThrow(() -> new ExceptionApi401("사용자를 찾을 수 없습니다."));
    }
}
