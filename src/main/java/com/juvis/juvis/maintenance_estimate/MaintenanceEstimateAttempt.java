package com.juvis.juvis.maintenance_estimate;

import com.juvis.juvis.maintenance.Maintenance;
import com.juvis.juvis.vendor_worker.VendorWorker;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_estimate_attempt", uniqueConstraints = @UniqueConstraint(name = "uk_maintenance_attempt", columnNames = {
        "maintenance_id", "attempt_no" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MaintenanceEstimateAttempt {

    public enum HqDecision {
        PENDING, APPROVED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ 어떤 유지보수 요청의 견적인가
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_id", nullable = false)
    private Maintenance maintenance;

    // ✅ 1차/2차
    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "estimate_amount", nullable = false, length = 50)
    private String estimateAmount;

    @Column(name = "estimate_comment", columnDefinition = "TEXT")
    private String estimateComment;

    private LocalDate workStartDate;
    private LocalDate workEndDate;

    @Column(name = "vendor_submitted_at", nullable = false)
    private LocalDateTime vendorSubmittedAt;

    // ✅✅✅ 작업자 스냅샷 (선택사항)
    @Column(name = "worker_id")
    private Long workerId;

    @Column(name = "worker_team_label", length = 50)
    private String workerTeamLabel;

    @Column(name = "worker_name", length = 100)
    private String workerName;

    @Column(name = "worker_phone", length = 50)
    private String workerPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "hq_decision", nullable = false, length = 20)
    private HqDecision hqDecision;

    private LocalDateTime hqDecidedAt;

    @Column(name = "hq_decided_by_name", length = 100)
    private String hqDecidedByName;

    @Column(name = "hq_reject_reason", columnDefinition = "TEXT")
    private String hqRejectReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public void setWorkerSnapshot(VendorWorker w) {
        if (w == null) {
            this.workerId = null;
            this.workerTeamLabel = null;
            this.workerName = null;
            this.workerPhone = null;
            return;
        }
        this.workerId = w.getId();
        this.workerTeamLabel = w.getTeamLabel();
        this.workerName = w.getName();
        this.workerPhone = w.getPhone();
    }

    public static MaintenanceEstimateAttempt create(
        Maintenance m,
        int attemptNo,
        String amount,
        String comment,
        LocalDate start,
        LocalDate end,
        LocalDateTime submittedAt
) {
    return MaintenanceEstimateAttempt.builder()
            .maintenance(m)
            .attemptNo(attemptNo)
            .estimateAmount(amount)
            .estimateComment(comment)
            .workStartDate(start)
            .workEndDate(end)
            .vendorSubmittedAt(submittedAt)
            .hqDecision(HqDecision.PENDING)
            .createdAt(LocalDateTime.now())
            .build();
}

    public void approve(String hqName) {
        this.hqDecision = HqDecision.APPROVED;
        this.hqDecidedAt = LocalDateTime.now();
        this.hqDecidedByName = hqName;
        this.hqRejectReason = null;
    }

    public void reject(String hqName, String reason) {
        this.hqDecision = HqDecision.REJECTED;
        this.hqDecidedAt = LocalDateTime.now();
        this.hqDecidedByName = hqName;
        this.hqRejectReason = reason;
    }
}
