package com.juvis.juvis.maintenance_estimate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaintenanceEstimateAttemptRepository extends JpaRepository<MaintenanceEstimateAttempt, Long> {
    List<MaintenanceEstimateAttempt> findByMaintenance_IdOrderByAttemptNoAsc(Long maintenanceId);

    Optional<MaintenanceEstimateAttempt> findByMaintenance_IdAndAttemptNo(Long maintenanceId, int attemptNo);

    Optional<MaintenanceEstimateAttempt> findTopByMaintenance_IdOrderByAttemptNoDesc(Long maintenanceId);

    Optional<MaintenanceEstimateAttempt> findTopByMaintenance_IdAndHqDecisionOrderByAttemptNoDesc(
            Long maintenanceId,
            MaintenanceEstimateAttempt.HqDecision hqDecision);

    @Query("""
              select a
              from MaintenanceEstimateAttempt a
              where a.maintenance.id in :mids
                and a.hqDecision = com.juvis.juvis.maintenance_estimate.MaintenanceEstimateAttempt$HqDecision.APPROVED
                and a.attemptNo = (
                    select max(a2.attemptNo)
                    from MaintenanceEstimateAttempt a2
                    where a2.maintenance.id = a.maintenance.id
                      and a2.hqDecision = com.juvis.juvis.maintenance_estimate.MaintenanceEstimateAttempt$HqDecision.APPROVED
                )
            """)
    List<MaintenanceEstimateAttempt> findLatestApprovedAttempts(
            @Param("mids") List<Long> mids);

}
