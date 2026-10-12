package com.racetracker.controller;

import com.racetracker.dto.race.RaceCategoryDto;
import com.racetracker.dto.race.RaceCategoryRequest;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.RaceCategoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}/categories")
public class RaceCategoryController {

    private final RaceCategoryService raceCategoryService;
    private final RaceAccessGuard raceAccessGuard;

    public RaceCategoryController(RaceCategoryService raceCategoryService, RaceAccessGuard raceAccessGuard) {
        this.raceCategoryService = raceCategoryService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @GetMapping("")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<RaceCategoryDto>> list(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(raceCategoryService.list(raceId));
    }

    @PostMapping("")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceCategoryDto> create(@PathVariable UUID raceId, @Valid @RequestBody RaceCategoryRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(raceCategoryService.create(raceId, request));
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceCategoryDto> update(
        @PathVariable UUID raceId,
        @PathVariable UUID categoryId,
        @Valid @RequestBody RaceCategoryRequest request
    ) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(raceCategoryService.update(raceId, categoryId, request));
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID raceId, @PathVariable UUID categoryId) {
        raceAccessGuard.assertCanManage(raceId);
        raceCategoryService.delete(raceId, categoryId);
        return ResponseEntity.noContent().build();
    }
}
