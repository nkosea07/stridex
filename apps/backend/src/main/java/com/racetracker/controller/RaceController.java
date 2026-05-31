package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.race.RaceCreateRequest;
import com.racetracker.dto.race.RaceDto;
import com.racetracker.dto.race.RaceStatusUpdateRequest;
import com.racetracker.dto.race.RaceUpdateRequest;
import com.racetracker.service.RaceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races")
public class RaceController {

    private final RaceService raceService;

    public RaceController(RaceService raceService) {
        this.raceService = raceService;
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
    public ResponseEntity<RaceDto> create(@Valid @RequestBody RaceCreateRequest request) {
        return ResponseEntity.ok(raceService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceDto> update(@PathVariable UUID id, @RequestBody RaceUpdateRequest request) {
        return ResponseEntity.ok(raceService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceDto> updateStatus(@PathVariable UUID id, @Valid @RequestBody RaceStatusUpdateRequest request) {
        return ResponseEntity.ok(raceService.updateStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ApiMessageResponse> delete(@PathVariable UUID id) {
        raceService.delete(id);
        return ResponseEntity.ok(new ApiMessageResponse("Race deleted"));
    }
}
