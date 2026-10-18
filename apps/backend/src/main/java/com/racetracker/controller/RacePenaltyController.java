package com.racetracker.controller;

import com.racetracker.dto.participant.PenaltyDto;
import com.racetracker.dto.participant.PenaltyRequest;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.PenaltyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}/participants/{participantId}/penalties")
public class RacePenaltyController {

    private final PenaltyService penaltyService;
    private final AuditEventService auditEventService;
    private final RaceAccessGuard raceAccessGuard;

    public RacePenaltyController(PenaltyService penaltyService, AuditEventService auditEventService,
                                 RaceAccessGuard raceAccessGuard) {
        this.penaltyService = penaltyService;
        this.auditEventService = auditEventService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<PenaltyDto>> list(@PathVariable UUID raceId, @PathVariable UUID participantId) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(penaltyService.list(raceId, participantId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<PenaltyDto> add(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId,
        @Valid @RequestBody PenaltyRequest request,
        Authentication authentication
    ) {
        raceAccessGuard.assertCanManage(raceId);
        PenaltyDto penalty = penaltyService.add(raceId, participantId, request, authentication.getName());
        auditEventService.recordForActor("RESULT_PENALTY_ADDED", authentication.getName(), "PARTICIPANT",
            participantId.toString(), "SUCCESS", Map.of("seconds", String.valueOf(request.getSeconds())));
        return ResponseEntity.ok(penalty);
    }

    @DeleteMapping("/{penaltyId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<Void> remove(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId,
        @PathVariable UUID penaltyId,
        Authentication authentication
    ) {
        raceAccessGuard.assertCanManage(raceId);
        penaltyService.remove(raceId, penaltyId);
        auditEventService.recordForActor("RESULT_PENALTY_REMOVED", authentication.getName(), "PARTICIPANT",
            participantId.toString(), "SUCCESS", Map.of("penaltyId", penaltyId.toString()));
        return ResponseEntity.noContent().build();
    }
}
