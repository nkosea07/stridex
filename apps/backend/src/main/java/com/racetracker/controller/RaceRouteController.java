package com.racetracker.controller;

import com.racetracker.dto.route.RoutePointDto;
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

    public RaceRouteController(RaceRouteService raceRouteService) {
        this.raceRouteService = raceRouteService;
    }

    @PostMapping(value = "/v${racetracker.api.version}/races/{raceId}/route/gpx",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<List<RoutePointDto>> importGpx(
        @PathVariable UUID raceId,
        @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(raceRouteService.importGpx(raceId, file));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/route")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE')")
    public ResponseEntity<List<RoutePointDto>> route(@PathVariable UUID raceId) {
        return ResponseEntity.ok(raceRouteService.getRoute(raceId));
    }

    @GetMapping("/v${racetracker.api.version}/public/races/{raceId}/route")
    public ResponseEntity<List<RoutePointDto>> publicRoute(@PathVariable UUID raceId) {
        return ResponseEntity.ok(raceRouteService.getRoute(raceId));
    }
}
