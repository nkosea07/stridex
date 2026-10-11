package com.racetracker.repository;

import com.racetracker.model.EmergencyAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmergencyAlertRepository extends JpaRepository<EmergencyAlert, UUID> {

    List<EmergencyAlert> findByRaceIdOrderByCreatedAtDesc(UUID raceId);

    List<EmergencyAlert> findByRaceIdAndStatusOrderByCreatedAtDesc(UUID raceId, EmergencyAlert.EmergencyStatus status);
}
