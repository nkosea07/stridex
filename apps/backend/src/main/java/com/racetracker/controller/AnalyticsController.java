package com.racetracker.controller;

import com.racetracker.dto.analytics.DemographicsDto;
import com.racetracker.dto.analytics.RaceAnalyticsSummaryDto;
import com.racetracker.dto.analytics.RegistrationPointDto;
import com.racetracker.service.AnalyticsExportService;
import com.racetracker.service.AnalyticsService;
import com.racetracker.service.ExportedFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AnalyticsExportService analyticsExportService;

    public AnalyticsController(AnalyticsService analyticsService, AnalyticsExportService analyticsExportService) {
        this.analyticsService = analyticsService;
        this.analyticsExportService = analyticsExportService;
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<byte[]> export(
        @PathVariable UUID raceId,
        @RequestParam(defaultValue = "csv") String format
    ) {
        ExportedFile file = analyticsExportService.export(raceId, format);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
            .contentType(MediaType.parseMediaType(file.contentType()))
            .body(file.content());
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
