package com.racetracker.controller;

import com.racetracker.dto.emergency.EmergencyAlertDto;
import com.racetracker.dto.emergency.EmergencyAlertRequest;
import com.racetracker.dto.emergency.EmergencyAlertStatusRequest;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.CheckpointEmergencyService;
import com.racetracker.service.EmergencyAlertService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class EmergencyAlertController {

    private final EmergencyAlertService emergencyAlertService;
    private final AuditEventService auditEventService;
    private final RaceAccessGuard raceAccessGuard;
    private final CheckpointEmergencyService checkpointEmergencyService;

    public EmergencyAlertController(EmergencyAlertService emergencyAlertService, AuditEventService auditEventService,
                                    RaceAccessGuard raceAccessGuard, CheckpointEmergencyService checkpointEmergencyService) {
        this.emergencyAlertService = emergencyAlertService;
        this.auditEventService = auditEventService;
        this.raceAccessGuard = raceAccessGuard;
        this.checkpointEmergencyService = checkpointEmergencyService;
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/checkpoints/{checkpointId}/emergency")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL', 'VOLUNTEER')")
    public ResponseEntity<EmergencyAlertDto> raiseAtCheckpoint(
        @PathVariable UUID raceId, @PathVariable UUID checkpointId,
        @Valid @RequestBody EmergencyAlertRequest request, Authentication authentication
    ) {
        return ResponseEntity.accepted().body(
            checkpointEmergencyService.raise(raceId, checkpointId, request, authentication.getName()));
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/emergency")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN') or hasAuthority('CAP_ATHLETE')")
    public ResponseEntity<EmergencyAlertDto> raise(
        @PathVariable UUID raceId,
        @Valid @RequestBody EmergencyAlertRequest request,
        Authentication authentication
    ) {
        boolean staff = authentication.getAuthorities().stream().anyMatch(authority ->
            authority.getAuthority().equals("ROLE_SUPER_ADMIN") || authority.getAuthority().equals("ROLE_RACE_ADMIN"));
        EmergencyAlertDto alert;
        if (staff) {
            raceAccessGuard.assertCanManage(raceId);
            alert = emergencyAlertService.raise(raceId, request, authentication.getName());
        } else {
            alert = emergencyAlertService.raiseForAthlete(raceId, request, authentication.getName());
        }
        auditEventService.recordForActor("EMERGENCY_ALERT_RAISED", authentication.getName(), "RACE", raceId.toString(),
            "SUCCESS", Map.of("alertId", alert.getId().toString()));
        return ResponseEntity.accepted().body(alert);
    }

    @PatchMapping("/v${racetracker.api.version}/races/{raceId}/emergency/{alertId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<EmergencyAlertDto> updateStatus(
        @PathVariable UUID raceId,
        @PathVariable UUID alertId,
        @Valid @RequestBody EmergencyAlertStatusRequest request,
        Authentication authentication
    ) {
        raceAccessGuard.assertCanReadStaff(raceId);
        EmergencyAlertDto alert = emergencyAlertService.updateStatus(raceId, alertId, request.status(),
            authentication.getName());
        auditEventService.recordForActor("EMERGENCY_ALERT_STATUS_CHANGED", authentication.getName(), "RACE",
            raceId.toString(), "SUCCESS", Map.of("alertId", alertId.toString(), "status", alert.getStatus()));
        return ResponseEntity.ok(alert);
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/emergency")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<List<EmergencyAlertDto>> list(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(emergencyAlertService.list(raceId));
    }
}
