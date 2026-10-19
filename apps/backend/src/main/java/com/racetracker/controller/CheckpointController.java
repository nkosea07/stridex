package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.checkpoint.*;
import com.racetracker.service.CheckpointExportService;
import com.racetracker.service.CheckpointService;
import com.racetracker.service.ExportedFile;
import com.racetracker.security.RaceAccessGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races")
public class CheckpointController {

    private final CheckpointService checkpointService;
    private final CheckpointExportService checkpointExportService;
    private final RaceAccessGuard raceAccessGuard;

    public CheckpointController(CheckpointService checkpointService, CheckpointExportService checkpointExportService,
                                RaceAccessGuard raceAccessGuard) {
        this.checkpointService = checkpointService;
        this.checkpointExportService = checkpointExportService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @GetMapping("/{raceId}/checkpoints")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')")
    public ResponseEntity<List<CheckpointDto>> list(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanRead(raceId);
        return ResponseEntity.ok(checkpointService.listByRace(raceId));
    }

    @GetMapping("/{raceId}/checkpoints/export")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<byte[]> export(
        @PathVariable UUID raceId,
        @RequestParam(defaultValue = "csv") String format
    ) {
        raceAccessGuard.assertCanReadStaff(raceId);
        ExportedFile file = checkpointExportService.export(raceId, format);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
            .contentType(MediaType.parseMediaType(file.contentType()))
            .body(file.content());
    }

    @PostMapping("/{raceId}/checkpoints")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<CheckpointDto> create(@PathVariable UUID raceId, @Valid @RequestBody CheckpointCreateRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(checkpointService.create(raceId, request));
    }

    @PutMapping("/{raceId}/checkpoints/{checkpointId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<CheckpointDto> update(
        @PathVariable UUID raceId,
        @PathVariable UUID checkpointId,
        @RequestBody CheckpointUpdateRequest request
    ) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(checkpointService.update(raceId, checkpointId, request));
    }

    @DeleteMapping("/{raceId}/checkpoints/{checkpointId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ApiMessageResponse> delete(@PathVariable UUID raceId, @PathVariable UUID checkpointId) {
        raceAccessGuard.assertCanManage(raceId);
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
        raceAccessGuard.assertCanAccessCheckpoint(raceId, checkpointId);
        return ResponseEntity.ok(checkpointService.record(raceId, checkpointId, request));
    }

    @PatchMapping("/{raceId}/checkpoints/{checkpointId}/records/{recordId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<CheckpointRecordDto> correctRecord(
        @PathVariable UUID raceId,
        @PathVariable UUID checkpointId,
        @PathVariable UUID recordId,
        @RequestBody CheckpointRecordCorrectionRequest request
    ) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(checkpointService.correctRecord(raceId, checkpointId, recordId, request));
    }

    @GetMapping("/{raceId}/checkpoints/{checkpointId}/records")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<CheckpointRecordDto>> listRecords(
        @PathVariable UUID raceId,
        @PathVariable UUID checkpointId
    ) {
        raceAccessGuard.assertCanAccessCheckpoint(raceId, checkpointId);
        return ResponseEntity.ok(checkpointService.listRecords(raceId, checkpointId));
    }
}
