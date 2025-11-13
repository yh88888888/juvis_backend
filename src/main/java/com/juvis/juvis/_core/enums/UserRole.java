package com.juvis.juvis._core.enums;

public enum UserRole {
    BRANCH,
    HQ,
    VENDOR;

    public String asAuthority() {
        return "ROLE_" + name();
    }
}
