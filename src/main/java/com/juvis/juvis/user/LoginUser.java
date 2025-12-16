package com.juvis.juvis.user;

import com.juvis.juvis._core.enums.UserRole;

public record LoginUser(Integer id, String username, UserRole role) {}