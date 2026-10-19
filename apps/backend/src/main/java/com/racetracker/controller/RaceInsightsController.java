package com.racetracker.controller;

import com.racetracker.dto.insights.ParticipantAnomalyDto;
import com.racetracker.dto.insights.RaceInsightsSummaryDto;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.RaceInsightsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}/insights")
public class RaceInsightsController {

    private final RaceInsightsService raceInsightsService;
    private final RaceAccessGuard raceAccessGuard;

    public RaceInsightsController(RaceInsightsService raceInsightsService, RaceAccessGuard raceAccessGuard) {
        this.raceInsightsService = raceInsightsService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<RaceInsightsSummaryDto> summary(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(raceInsightsService.summary(raceId));
    }

    @GetMapping("/anomalies")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<List<ParticipantAnomalyDto>> anomalies(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(raceInsightsService.anomalies(raceId));
    }
}
