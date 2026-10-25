package com.racetracker.service;

import com.racetracker.dto.emergency.EmergencyAlertDto;
import com.racetracker.dto.emergency.EmergencyAlertRequest;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.security.RaceAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class CheckpointEmergencyService {
    private final CheckpointRepository checkpointRepository;
    private final RaceAccessGuard raceAccessGuard;
    private final EmergencyAlertService emergencyAlertService;
    private final AuditEventService auditEventService;

    public CheckpointEmergencyService(CheckpointRepository checkpointRepository, RaceAccessGuard raceAccessGuard,
                                      EmergencyAlertService emergencyAlertService, AuditEventService auditEventService) {
        this.checkpointRepository = checkpointRepository;
        this.raceAccessGuard = raceAccessGuard;
        this.emergencyAlertService = emergencyAlertService;
        this.auditEventService = auditEventService;
    }

    @Transactional
    public EmergencyAlertDto raise(UUID raceId, UUID checkpointId, EmergencyAlertRequest request, String actorEmail) {
        if (!checkpointRepository.existsByRaceIdAndId(raceId, checkpointId)) {
            throw new ResourceNotFoundException("Checkpoint not found");
        }
        raceAccessGuard.assertCanAccessCheckpoint(raceId, checkpointId);
        EmergencyAlertDto alert = emergencyAlertService.raise(raceId, request, actorEmail);
        auditEventService.recordForActor("EMERGENCY_ALERT_RAISED", actorEmail, "RACE", raceId.toString(),
            "SUCCESS", Map.of("alertId", alert.getId().toString(), "checkpointId", checkpointId.toString()));
        return alert;
    }
}
