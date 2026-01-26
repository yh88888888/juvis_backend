package com.juvis.juvis._core.enums;

import lombok.Getter;

@Getter
public enum MaintenanceCategory {

    PAINTING("도장"),
    WALLPAPER("도배"),
    DOOR("도어"),
    DOOR_LOCK("도어락"),
    FURNITURE_REPAIR("가구수리"),
    SANITARY_FIXTURE("세면대 및 샤워기"),
    DRAINAGE("배수"),
    ELECTRICAL_OUTLET("전등 및 콘센트"),
    LEAK("누수"),
    AUTOMATIC_DOOR("자동문"),
    CCTV("CCTV"),
    ETC("기타");

    private final String displayName;

    MaintenanceCategory(String displayName) {
        this.displayName = displayName;
    }
}
