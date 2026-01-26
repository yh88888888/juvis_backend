package com.juvis.juvis.user_device;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juvis.juvis.user.LoginUser;
import com.juvis.juvis.user.User;
import com.juvis.juvis.user.UserRepository;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis._core.error.ex.ExceptionApi404;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDeviceService {

    private final UserDeviceRepository userDeviceRepository;
    private final UserRepository userRepository;

    @Transactional
    public void upsert(LoginUser loginUser, DeviceTokenRequest req) {
        if (loginUser == null)
            throw new ExceptionApi403("로그인이 필요합니다.");

        User me = userRepository.findById(loginUser.id())
                .orElseThrow(() -> new ExceptionApi404("사용자 없음"));

        String token = req.token().trim();
        String platform = req.platform().trim().toUpperCase();

        userDeviceRepository.findByToken(token).ifPresentOrElse(existing -> {
            // ✅ 토큰이 이미 있으면: user까지 최신화 (다른 계정으로 로그인한 케이스 방지)
            existing.rebind(me, platform);
            userDeviceRepository.update(existing);
        }, () -> {
            userDeviceRepository.save(UserDevice.of(me, platform, token));
        });
    }
}
