package com.racetracker.controller;

import com.racetracker.dto.race.RaceWaveDto;
import com.racetracker.dto.race.RaceWaveRequest;
import com.racetracker.dto.race.WaveAssignmentRequest;
import com.racetracker.dto.race.WaveStartRequest;
import com.racetracker.service.WaveService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}/waves")
public class RaceWaveController {

    private final WaveService waveService;

    public RaceWaveController(WaveService waveService) {
        this.waveService = waveService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<List<RaceWaveDto>> list(@PathVariable UUID raceId) {
        return ResponseEntity.ok(waveService.list(raceId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceWaveDto> create(@PathVariable UUID raceId, @Valid @RequestBody RaceWaveRequest request) {
        return ResponseEntity.ok(waveService.create(raceId, request));
    }

    @PutMapping("/{waveId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceWaveDto> update(@PathVariable UUID raceId, @PathVariable UUID waveId,
                                              @Valid @RequestBody RaceWaveRequest request) {
        return ResponseEntity.ok(waveService.update(raceId, waveId, request));
    }

    @DeleteMapping("/{waveId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID raceId, @PathVariable UUID waveId) {
        waveService.delete(raceId, waveId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{waveId}/start")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<RaceWaveDto> start(@PathVariable UUID raceId, @PathVariable UUID waveId,
                                             @Valid @RequestBody(required = false) WaveStartRequest request) {
        return ResponseEntity.ok(waveService.start(raceId, waveId, request));
    }

    @PostMapping("/{waveId}/participants")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<Map<String, Integer>> assign(@PathVariable UUID raceId, @PathVariable UUID waveId,
                                                       @Valid @RequestBody WaveAssignmentRequest request) {
        return ResponseEntity.ok(Map.of("assigned", waveService.assign(raceId, waveId, request)));
    }
}
