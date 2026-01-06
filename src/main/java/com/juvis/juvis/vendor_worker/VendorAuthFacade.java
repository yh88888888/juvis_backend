package com.juvis.juvis.vendor_worker;

import org.springframework.stereotype.Component;

import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis._core.error.ex.ExceptionApi403;
import com.juvis.juvis.user.LoginUser;

@Component
public class VendorAuthFacade {

    public Long requireVendorId(LoginUser loginUser) {
        if (loginUser == null) {
            throw new ExceptionApi403("인증 필요");
        }
        if (loginUser.role() != UserRole.VENDOR) {
            throw new ExceptionApi403("Vendor 권한이 필요합니다.");
        }
        return loginUser.id().longValue();
    }
}