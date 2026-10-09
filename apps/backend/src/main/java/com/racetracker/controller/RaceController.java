package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.race.RaceCreateRequest;
import com.racetracker.dto.race.RaceDto;
import com.racetracker.dto.race.RaceStatusUpdateRequest;
import com.racetracker.dto.race.RaceUpdateRequest;
import com.racetracker.service.RaceService;
import com.racetracker.service.AuditEventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races")
public class RaceController {

    private final RaceService raceService;
    private final AuditEventService auditEventService;

    public RaceController(RaceService raceService, AuditEventService auditEventService) {
        this.raceService = raceService;
        this.auditEventService = auditEventService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')")
    public ResponseEntity<Page<RaceDto>> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Boolean upcoming,
        Pageable pageable
    ) {
        return ResponseEntity.ok(raceService.list(status, upcoming, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')")
    public ResponseEntity<RaceDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(raceService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceDto> create(@Valid @RequestBody RaceCreateRequest request, Authentication authentication) {
        RaceDto created = raceService.create(request);
        auditEventService.recordForActor("RACE_CREATED", authentication.getName(), "RACE", created.getId().toString(),
            "SUCCESS", java.util.Map.of());
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceDto> update(@PathVariable UUID id, @RequestBody RaceUpdateRequest request, Authentication authentication) {
        RaceDto updated = raceService.update(id, request);
        auditEventService.recordForActor("RACE_UPDATED", authentication.getName(), "RACE", id.toString(), "SUCCESS", java.util.Map.of());
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceDto> updateStatus(@PathVariable UUID id, @Valid @RequestBody RaceStatusUpdateRequest request, Authentication authentication) {
        RaceDto updated = raceService.updateStatus(id, request.getStatus());
        auditEventService.recordForActor("RACE_STATUS_CHANGED", authentication.getName(), "RACE", id.toString(),
            "SUCCESS", java.util.Map.of("status", request.getStatus()));
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ApiMessageResponse> delete(@PathVariable UUID id, Authentication authentication) {
        raceService.delete(id);
        auditEventService.recordForActor("RACE_DELETED", authentication.getName(), "RACE", id.toString(), "SUCCESS", java.util.Map.of());
        return ResponseEntity.ok(new ApiMessageResponse("Race deleted"));
    }
}
