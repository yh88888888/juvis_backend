package com.juvis.juvis.user_device;

import jakarta.validation.constraints.NotBlank;

public record DeviceTokenRequest(
        @NotBlank String platform,   // ANDROID / IOS
        @NotBlank String token
) {}

