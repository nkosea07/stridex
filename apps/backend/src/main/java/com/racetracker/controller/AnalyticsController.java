package com.racetracker.controller;

import com.racetracker.dto.analytics.DemographicsDto;
import com.racetracker.dto.analytics.RaceAnalyticsSummaryDto;
import com.racetracker.dto.analytics.RegistrationPointDto;
import com.racetracker.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<RaceAnalyticsSummaryDto> summary(@PathVariable UUID raceId) {
        return ResponseEntity.ok(analyticsService.summary(raceId));
    }

    @GetMapping("/demographics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<DemographicsDto> demographics(@PathVariable UUID raceId) {
        return ResponseEntity.ok(analyticsService.demographics(raceId));
    }

    @GetMapping("/registrations")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<RegistrationPointDto>> registrations(
        @PathVariable UUID raceId,
        @RequestParam(defaultValue = "30") int days
    ) {
        return ResponseEntity.ok(analyticsService.registrationSeries(raceId, days));
    }
}
