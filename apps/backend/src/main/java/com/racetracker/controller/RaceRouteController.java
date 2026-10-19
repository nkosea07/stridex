package com.racetracker.controller;

import com.racetracker.dto.route.RoutePointDto;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.RaceRouteService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
public class RaceRouteController {

    private final RaceRouteService raceRouteService;
    private final RaceAccessGuard raceAccessGuard;

    public RaceRouteController(RaceRouteService raceRouteService, RaceAccessGuard raceAccessGuard) {
        this.raceRouteService = raceRouteService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @PostMapping(value = "/v${racetracker.api.version}/races/{raceId}/route/gpx",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<List<RoutePointDto>> importGpx(
        @PathVariable UUID raceId,
        @RequestPart("file") MultipartFile file
    ) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(raceRouteService.importGpx(raceId, file));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/route")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE')")
    public ResponseEntity<List<RoutePointDto>> route(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanRead(raceId);
        return ResponseEntity.ok(raceRouteService.getRoute(raceId));
    }

    @GetMapping(value = "/v${racetracker.api.version}/races/{raceId}/route/gpx", produces = "application/gpx+xml")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<String> exportGpx(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"route.gpx\"")
            .contentType(MediaType.parseMediaType("application/gpx+xml"))
            .body(raceRouteService.exportGpx(raceId));
    }

    @GetMapping("/v${racetracker.api.version}/public/races/{raceId}/route")
    public ResponseEntity<List<RoutePointDto>> publicRoute(@PathVariable UUID raceId) {
        return ResponseEntity.ok(raceRouteService.getRoute(raceId));
    }
}
