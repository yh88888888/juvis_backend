package com.juvis.juvis.maintenance_photo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenancePhotoRepository extends JpaRepository<MaintenancePhoto, Long> {

List<MaintenancePhoto> findByMaintenanceId(Long maintenanceId);
}

