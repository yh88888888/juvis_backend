package com.juvis.juvis.vendor_worker;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

public class VendorWorkerDtos {

    @Getter @Setter
    public static class CreateReq {
        private String teamLabel; // optional
        @NotBlank
        private String name;      // required
        private String phone;     // optional
    }

    @Getter @Builder
    public static class ItemRes {
        private Long id;
        private String teamLabel;
        private String name;
        private String phone;

        public String getDisplay() {
            // 드롭다운 표기용 (서버에서 만들어 내려줘도 됨)
            if (teamLabel == null || teamLabel.isBlank()) return name;
            return teamLabel + " " + name;
        }
    }
}