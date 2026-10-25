package com.racetracker.controller;

import com.racetracker.dto.race.RaceStageDto;
import com.racetracker.dto.race.RaceStageRequest;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.RaceStageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}/stages")
public class RaceStageController {

    private final RaceStageService stageService;
    private final RaceAccessGuard raceAccessGuard;

    public RaceStageController(RaceStageService stageService, RaceAccessGuard raceAccessGuard) {
        this.stageService = stageService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL', 'VOLUNTEER', 'ATHLETE', 'SPECTATOR')")
    public ResponseEntity<List<RaceStageDto>> list(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanRead(raceId);
        return ResponseEntity.ok(stageService.list(raceId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceStageDto> create(@PathVariable UUID raceId, @Valid @RequestBody RaceStageRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(stageService.create(raceId, request));
    }

    @PutMapping("/{stageId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceStageDto> update(@PathVariable UUID raceId, @PathVariable UUID stageId,
                                               @Valid @RequestBody RaceStageRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(stageService.update(raceId, stageId, request));
    }

    @DeleteMapping("/{stageId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID raceId, @PathVariable UUID stageId) {
        raceAccessGuard.assertCanManage(raceId);
        stageService.delete(raceId, stageId);
        return ResponseEntity.noContent().build();
    }
}
