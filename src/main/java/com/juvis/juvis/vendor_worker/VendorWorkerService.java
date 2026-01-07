package com.juvis.juvis.vendor_worker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VendorWorkerService {

        private final VendorWorkerRepository repo;

        @Transactional(readOnly = true)
        public List<VendorWorkerDtos.ItemRes> list(Long vendorId) {
                return repo.findByVendorIdAndIsActiveTrueOrderByIdDesc(vendorId).stream()
                                .map(w -> VendorWorkerDtos.ItemRes.builder()
                                                .id(w.getId())
                                                .teamLabel(w.getTeamLabel())
                                                .name(w.getName())
                                                .phone(w.getPhone())
                                                .build())
                                .toList();
        }

        @Transactional
        public VendorWorkerDtos.ItemRes create(Long vendorId, VendorWorkerDtos.CreateReq req) {
                VendorWorker saved = repo.save(VendorWorker.builder()
                                .vendorId(vendorId)
                                .teamLabel(req.getTeamLabel())
                                .name(req.getName())
                                .phone(req.getPhone())
                                .isActive(true)
                                .build());

                return VendorWorkerDtos.ItemRes.builder()
                                .id(saved.getId())
                                .teamLabel(saved.getTeamLabel())
                                .name(saved.getName())
                                .phone(saved.getPhone())
                                .build();
        }

        @Transactional
        public void delete(Long vendorId, Long workerId) {
                VendorWorker w = repo.findByIdAndVendorId(workerId, vendorId)
                                .orElseThrow(() -> new IllegalArgumentException("작업자를 찾을 수 없습니다."));
                // 삭제 대신 비활성 권장 (참조 무결성/히스토리)
                w.setIsActive(false);
        }
}
