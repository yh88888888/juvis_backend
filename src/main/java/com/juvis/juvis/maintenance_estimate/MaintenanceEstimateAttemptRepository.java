package com.juvis.juvis.maintenance_estimate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaintenanceEstimateAttemptRepository extends JpaRepository<MaintenanceEstimateAttempt, Long> {
    List<MaintenanceEstimateAttempt> findByMaintenance_IdOrderByAttemptNoAsc(Long maintenanceId);

    Optional<MaintenanceEstimateAttempt> findByMaintenance_IdAndAttemptNo(Long maintenanceId, int attemptNo);
}
