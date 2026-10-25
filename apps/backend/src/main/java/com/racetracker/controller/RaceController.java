package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.race.RaceCloneRequest;
import com.racetracker.dto.race.RaceCreateRequest;
import com.racetracker.dto.race.RaceAdminAssignmentRequest;
import com.racetracker.dto.race.RaceDto;
import com.racetracker.dto.race.RaceStatusUpdateRequest;
import com.racetracker.dto.race.RaceUpdateRequest;
import com.racetracker.security.RaceAccessGuard;
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
    private final RaceAccessGuard raceAccessGuard;

    public RaceController(RaceService raceService, AuditEventService auditEventService, RaceAccessGuard raceAccessGuard) {
        this.raceService = raceService;
        this.auditEventService = auditEventService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')")
    public ResponseEntity<Page<RaceDto>> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Boolean upcoming,
        @RequestParam(required = false) Boolean includeArchived,
        Pageable pageable
    ) {
        return ResponseEntity.ok(raceService.list(status, upcoming, Boolean.TRUE.equals(includeArchived), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')")
    public ResponseEntity<RaceDto> get(@PathVariable UUID id) {
        raceAccessGuard.assertCanRead(id);
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

    @PostMapping("/{id}/clone")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceDto> clone(@PathVariable UUID id, @Valid @RequestBody RaceCloneRequest request, Authentication authentication) {
        raceAccessGuard.assertCanManage(id);
        RaceDto created = raceService.clone(id, request);
        auditEventService.recordForActor("RACE_CLONED", authentication.getName(), "RACE", created.getId().toString(),
            "SUCCESS", java.util.Map.of("sourceRaceId", id.toString()));
        return ResponseEntity.ok(created);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceDto> archive(@PathVariable UUID id, Authentication authentication) {
        raceAccessGuard.assertCanManage(id);
        RaceDto archived = raceService.archive(id);
        auditEventService.recordForActor("RACE_ARCHIVED", authentication.getName(), "RACE", id.toString(), "SUCCESS", java.util.Map.of());
        return ResponseEntity.ok(archived);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceDto> restore(@PathVariable UUID id, Authentication authentication) {
        raceAccessGuard.assertCanManage(id);
        RaceDto restored = raceService.restore(id);
        auditEventService.recordForActor("RACE_RESTORED", authentication.getName(), "RACE", id.toString(), "SUCCESS", java.util.Map.of());
        return ResponseEntity.ok(restored);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceDto> update(@PathVariable UUID id, @RequestBody RaceUpdateRequest request, Authentication authentication) {
        raceAccessGuard.assertCanManage(id);
        RaceDto updated = raceService.update(id, request);
        auditEventService.recordForActor("RACE_UPDATED", authentication.getName(), "RACE", id.toString(), "SUCCESS", java.util.Map.of());
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceDto> updateStatus(@PathVariable UUID id, @Valid @RequestBody RaceStatusUpdateRequest request, Authentication authentication) {
        raceAccessGuard.assertCanManage(id);
        RaceDto updated = raceService.updateStatus(id, request.getStatus(), request.getReason());
        auditEventService.recordForActor("RACE_STATUS_CHANGED", authentication.getName(), "RACE", id.toString(),
            "SUCCESS", java.util.Map.of("status", request.getStatus(), "reason",
                request.getReason() == null ? "" : request.getReason()));
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiMessageResponse> assignAdmin(
        @PathVariable UUID id,
        @Valid @RequestBody RaceAdminAssignmentRequest request,
        Authentication authentication
    ) {
        raceService.assignAdmin(id, request.getUserId(), authentication.getName());
        return ResponseEntity.ok(new ApiMessageResponse("Race admin assigned"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ApiMessageResponse> delete(@PathVariable UUID id, Authentication authentication) {
        raceAccessGuard.assertCanManage(id);
        raceService.delete(id);
        auditEventService.recordForActor("RACE_DELETED", authentication.getName(), "RACE", id.toString(), "SUCCESS", java.util.Map.of());
        return ResponseEntity.ok(new ApiMessageResponse("Race deleted"));
    }
}
