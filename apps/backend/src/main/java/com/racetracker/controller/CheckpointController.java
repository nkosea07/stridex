package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.checkpoint.*;
import com.racetracker.service.CheckpointService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races")
public class CheckpointController {

    private final CheckpointService checkpointService;

    public CheckpointController(CheckpointService checkpointService) {
        this.checkpointService = checkpointService;
    }

    @GetMapping("/{raceId}/checkpoints")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')")
    public ResponseEntity<List<CheckpointDto>> list(@PathVariable UUID raceId) {
        return ResponseEntity.ok(checkpointService.listByRace(raceId));
    }

    @PostMapping("/{raceId}/checkpoints")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<CheckpointDto> create(@PathVariable UUID raceId, @Valid @RequestBody CheckpointCreateRequest request) {
        return ResponseEntity.ok(checkpointService.create(raceId, request));
    }

    @PutMapping("/{raceId}/checkpoints/{checkpointId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<CheckpointDto> update(
        @PathVariable UUID raceId,
        @PathVariable UUID checkpointId,
        @RequestBody CheckpointUpdateRequest request
    ) {
        return ResponseEntity.ok(checkpointService.update(raceId, checkpointId, request));
    }

    @DeleteMapping("/{raceId}/checkpoints/{checkpointId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ApiMessageResponse> delete(@PathVariable UUID raceId, @PathVariable UUID checkpointId) {
        checkpointService.delete(raceId, checkpointId);
        return ResponseEntity.ok(new ApiMessageResponse("Checkpoint deleted"));
    }

    @PostMapping("/{raceId}/checkpoints/{checkpointId}/record")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<CheckpointRecordDto> record(
        @PathVariable UUID raceId,
        @PathVariable UUID checkpointId,
        @RequestBody CheckpointRecordRequest request
    ) {
        return ResponseEntity.ok(checkpointService.record(raceId, checkpointId, request));
    }
}
