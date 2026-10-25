package com.racetracker.controller;

import com.racetracker.dto.race.RaceSponsorDto;
import com.racetracker.dto.race.RaceSponsorRequest;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.RaceSponsorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}/sponsors")
public class RaceSponsorController {

    private final RaceSponsorService sponsorService;
    private final RaceAccessGuard raceAccessGuard;

    public RaceSponsorController(RaceSponsorService sponsorService, RaceAccessGuard raceAccessGuard) {
        this.sponsorService = sponsorService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL', 'VOLUNTEER', 'ATHLETE', 'SPECTATOR')")
    public ResponseEntity<List<RaceSponsorDto>> list(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanRead(raceId);
        return ResponseEntity.ok(sponsorService.list(raceId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceSponsorDto> create(@PathVariable UUID raceId, @Valid @RequestBody RaceSponsorRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(sponsorService.create(raceId, request));
    }

    @PutMapping("/{sponsorId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceSponsorDto> update(@PathVariable UUID raceId, @PathVariable UUID sponsorId,
                                                 @Valid @RequestBody RaceSponsorRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(sponsorService.update(raceId, sponsorId, request));
    }

    @DeleteMapping("/{sponsorId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID raceId, @PathVariable UUID sponsorId) {
        raceAccessGuard.assertCanManage(raceId);
        sponsorService.delete(raceId, sponsorId);
        return ResponseEntity.noContent().build();
    }
}
