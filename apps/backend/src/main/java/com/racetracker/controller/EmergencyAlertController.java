package com.racetracker.controller;

import com.racetracker.dto.emergency.EmergencyAlertDto;
import com.racetracker.dto.emergency.EmergencyAlertRequest;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
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

    public EmergencyAlertController(EmergencyAlertService emergencyAlertService, AuditEventService auditEventService,
                                    RaceAccessGuard raceAccessGuard) {
        this.emergencyAlertService = emergencyAlertService;
        this.auditEventService = auditEventService;
        this.raceAccessGuard = raceAccessGuard;
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

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/emergency")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<List<EmergencyAlertDto>> list(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(emergencyAlertService.list(raceId));
    }
}
