package com.juvis.juvis._core.enums;


// 상태값
public enum MaintenanceStatus {
    DRAFT,             // 지점 임시 저장
    REQUESTED,         // 지점이 공식 제출
    ESTIMATING,        // HQ가 벤더에게 견적 의뢰
    APPROVAL_PENDING,  // 벤더가 견적 제출, HQ 승인 대기
    IN_PROGRESS,       // 공사 진행 중
    COMPLETED,         // 완료
    REJECTED           // HQ 반려
}