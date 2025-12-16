package com.juvis.juvis.user.authToken;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RefreshTokenResponse {
    private String accessToken;
    
}
