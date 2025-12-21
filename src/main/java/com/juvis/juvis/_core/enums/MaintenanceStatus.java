package com.juvis.juvis._core.enums;


// 상태값
public enum MaintenanceStatus {
    DRAFT,               // 지점 임시 저장
    REQUESTED,           // 지점 제출 (HQ 1차 액션 가능)

    HQ1_REJECTED,        // ✅ HQ 1차 반려 (Vendor로 안 감)

    ESTIMATING,          // HQ가 Vendor에게 견적 의뢰(1차 승인 후)
    APPROVAL_PENDING,    // Vendor 견적 제출, HQ 2차 승인 대기

    HQ2_REJECTED,        // ✅ HQ 2차 반려 (견적 반려)

    IN_PROGRESS,         // HQ 2차 승인 후 공사 진행
    COMPLETED            // 완료
}