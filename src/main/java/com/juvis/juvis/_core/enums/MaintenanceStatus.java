package com.juvis.juvis._core.enums;

// 상태값
public enum MaintenanceStatus {
    DRAFT("임시 저장"),
    REQUESTED("요청서 제출"),//지점
    HQ1_REJECTED("요청서 반려"), //본사
    ESTIMATING("견적 제출필요"), //본사
    APPROVAL_PENDING("본사 승인대기"), //업체
    IN_PROGRESS("작업 중"), //본사
    COMPLETED("작업 완료"), //업체
    HQ2_REJECTED("견적 반려"); //본사사

    private final String kr;

    MaintenanceStatus(String kr) {
        this.kr = kr;
    }

    public String kr() {
        return kr;
    }
}
