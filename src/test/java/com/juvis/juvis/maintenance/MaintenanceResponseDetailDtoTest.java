package com.juvis.juvis.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.juvis.juvis._core.enums.MaintenanceCategory;
import com.juvis.juvis._core.enums.MaintenanceStatus;
import com.juvis.juvis.user.User;

class MaintenanceResponseDetailDtoTest {

    @Test
    void forBranch_masks_attempts_but_keeps_worker_snapshot_from_latest_attempt() {
        // given: Maintenance mock
        var m = mock(Maintenance.class);

        when(m.getId()).thenReturn(2L);
        when(m.getTitle()).thenReturn("t");
        when(m.getDescription()).thenReturn("d");
        when(m.getStatus()).thenReturn(MaintenanceStatus.IN_PROGRESS);

        // category (enum 반환)
        when(m.getCategory()).thenReturn(MaintenanceCategory.ELECTRICAL_OUTLET);

        // vendor
        var vendor = mock(User.class);
        when(vendor.getName()).thenReturn("아이디진정성");
        when(vendor.getPhone()).thenReturn("070-4838-7893");
        when(m.getVendor()).thenReturn(vendor);

        // branch/requester 등은 null이어도 무방 (생성자에서 null 처리)

        // latest attempt dto mock (핵심)
        var a1 = mock(MaintenanceResponse.EstimateAttemptDTO.class);
        when(a1.getEstimateAmount()).thenReturn("10000");
        when(a1.getEstimateComment()).thenReturn("c");
        when(a1.getWorkStartDate()).thenReturn(LocalDate.of(2026, 1, 7));
        when(a1.getWorkEndDate()).thenReturn(LocalDate.of(2026, 1, 7));
        when(a1.getWorkerName()).thenReturn("작업자A");
        when(a1.getWorkerPhone()).thenReturn("010-1111-2222");
        when(a1.getWorkerTeamLabel()).thenReturn("A팀");

        List<String> reqUrls = List.of("req1");
        List<String> resUrls = List.of();

        // when
        MaintenanceResponse.DetailDTO dto = MaintenanceResponse.DetailDTO.forBranch(
                m, reqUrls, resUrls, List.of(a1)
        );

        // then: worker snapshot is kept
        assertEquals("작업자A", dto.getWorkerName());
        assertEquals("010-1111-2222", dto.getWorkerPhone());
        assertEquals("A팀", dto.getWorkerTeamLabel());

        // then: attempts masked
        assertNotNull(dto.getEstimateAttempts());
        assertTrue(dto.getEstimateAttempts().isEmpty());

        // then: estimate fields masked
        assertNull(dto.getEstimateAmount());
        assertNull(dto.getEstimateComment());
        assertNull(dto.getEstimateApprovedByName());
        assertNull(dto.getEstimateApprovedAt());
        assertNull(dto.getVendorSubmittedAt());
        assertNull(dto.getEstimateResubmitCount());

        // vendor is still visible
        assertEquals("아이디진정성", dto.getVendorName());
        assertEquals("070-4838-7893", dto.getVendorPhone());

        // work dates should be visible for IN_PROGRESS
        assertNotNull(dto.getWorkStartDate());
        assertNotNull(dto.getWorkEndDate());
    }
}
