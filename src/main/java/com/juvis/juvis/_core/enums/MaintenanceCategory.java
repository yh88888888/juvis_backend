package com.juvis.juvis._core.enums;

import lombok.Getter;

@Getter
public enum MaintenanceCategory {

    ELECTRICAL_COMMUNICATION("전기·통신"),
    LIGHTING("조명"),
    HVAC("공조·환기"),
    WATER_SUPPLY_DRAINAGE("급·배수"),
    SAFETY_HYGIENE("안전·위생"),
    ETC("기타");

    private final String displayName;

    MaintenanceCategory(String displayName) {
        this.displayName = displayName;
    }
}
