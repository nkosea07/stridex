package com.racetracker.controller;

import com.racetracker.dto.publictracking.PublicAthleteTrackingResponse;
import com.racetracker.dto.publictracking.PublicRaceAthleteSummaryResponse;
import com.racetracker.dto.publictracking.PublicRaceHubResponse;
import com.racetracker.service.PublicTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/public/races/{raceId}")
public class PublicTrackingController {

    private final PublicTrackingService publicTrackingService;

    public PublicTrackingController(PublicTrackingService publicTrackingService) {
        this.publicTrackingService = publicTrackingService;
    }

    @GetMapping("/hub")
    public ResponseEntity<PublicRaceHubResponse> getHub(@PathVariable UUID raceId) {
        return ResponseEntity.ok(publicTrackingService.getRaceHub(raceId));
    }

    @GetMapping("/tracking/live")
    public ResponseEntity<List<PublicRaceAthleteSummaryResponse>> getLiveAthletes(@PathVariable UUID raceId) {
        return ResponseEntity.ok(publicTrackingService.getLiveAthletes(raceId, null));
    }

    @GetMapping("/tracking/search")
    public ResponseEntity<List<PublicRaceAthleteSummaryResponse>> searchAthletes(
        @PathVariable UUID raceId,
        @RequestParam("q") String query
    ) {
        return ResponseEntity.ok(publicTrackingService.getLiveAthletes(raceId, query));
    }

    @GetMapping("/tracking/athletes/{participantId}")
    public ResponseEntity<PublicAthleteTrackingResponse> getAthleteTracking(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId
    ) {
        return ResponseEntity.ok(publicTrackingService.getAthleteTracking(raceId, participantId));
    }
}
