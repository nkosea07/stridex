package com.racetracker.controller;

import com.racetracker.dto.participant.RaceRegistrationFieldDto;
import com.racetracker.dto.publictracking.HeatCellDto;
import com.racetracker.dto.publictracking.PublicAthleteTrackingResponse;
import com.racetracker.dto.publictracking.PublicParticipantTrackResponse;
import com.racetracker.dto.publictracking.PublicRaceAthleteSummaryResponse;
import com.racetracker.dto.publictracking.PublicRaceHubResponse;
import com.racetracker.dto.publictracking.PublicRaceLiveFeedResponse;
import com.racetracker.exception.ApiException;
import com.racetracker.service.ParticipantService;
import com.racetracker.service.PublicTrackingService;
import com.racetracker.security.ClientAddressResolver;
import com.racetracker.security.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/public/races/{raceId}")
public class PublicTrackingController {

    private final PublicTrackingService publicTrackingService;
    private final ParticipantService participantService;
    private final RequestRateLimiter requestRateLimiter;
    private final ClientAddressResolver clientAddressResolver;

    public PublicTrackingController(
        PublicTrackingService publicTrackingService,
        ParticipantService participantService,
        RequestRateLimiter requestRateLimiter,
        ClientAddressResolver clientAddressResolver
    ) {
        this.publicTrackingService = publicTrackingService;
        this.participantService = participantService;
        this.requestRateLimiter = requestRateLimiter;
        this.clientAddressResolver = clientAddressResolver;
    }

    @GetMapping("/hub")
    public ResponseEntity<PublicRaceHubResponse> getHub(@PathVariable UUID raceId, HttpServletRequest request) {
        rateLimit(raceId, request);
        return ResponseEntity.ok(publicTrackingService.getRaceHub(raceId));
    }

    @GetMapping("/registration-fields")
    public ResponseEntity<List<RaceRegistrationFieldDto>> getRegistrationFields(@PathVariable UUID raceId, HttpServletRequest request) {
        rateLimit(raceId, request);
        return ResponseEntity.ok(participantService.listRegistrationFields(raceId, true));
    }

    @GetMapping("/tracking/live")
    public ResponseEntity<List<PublicRaceAthleteSummaryResponse>> getLiveAthletes(@PathVariable UUID raceId, HttpServletRequest request) {
        rateLimit(raceId, request);
        return ResponseEntity.ok(publicTrackingService.getLiveAthletes(raceId, null));
    }

    @GetMapping("/tracking/live-feed")
    public ResponseEntity<PublicRaceLiveFeedResponse> getLiveFeed(
        @PathVariable UUID raceId,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "limit", defaultValue = "25") int limit,
        @RequestParam(value = "q", required = false) String query,
        HttpServletRequest request
    ) {
        rateLimit(raceId, request);
        return ResponseEntity.ok(publicTrackingService.getLiveFeed(raceId, query, cursor, limit));
    }

    @GetMapping("/tracking/search")
    public ResponseEntity<List<PublicRaceAthleteSummaryResponse>> searchAthletes(
        @PathVariable UUID raceId,
        @RequestParam("q") String query,
        HttpServletRequest request
    ) {
        rateLimit(raceId, request);
        return ResponseEntity.ok(publicTrackingService.getLiveAthletes(raceId, query));
    }

    @GetMapping("/tracking/athletes/{participantId}")
    public ResponseEntity<PublicAthleteTrackingResponse> getAthleteTracking(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId,
        HttpServletRequest request
    ) {
        rateLimit(raceId, request);
        return ResponseEntity.ok(publicTrackingService.getAthleteTracking(raceId, participantId));
    }

    @GetMapping("/tracking/athletes/{participantId}/track")
    public ResponseEntity<PublicParticipantTrackResponse> getAthleteTrack(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId,
        HttpServletRequest request
    ) {
        rateLimit(raceId, request);
        return ResponseEntity.ok(publicTrackingService.getAthleteTrack(raceId, participantId));
    }

    @GetMapping("/tracking/heatmap")
    public ResponseEntity<List<HeatCellDto>> getHeatmap(
        @PathVariable UUID raceId,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(required = false) Integer scale,
        HttpServletRequest request
    ) {
        rateLimit(raceId, request);
        return ResponseEntity.ok(publicTrackingService.getRaceHeatmap(raceId, parseTime(from), parseTime(to), scale));
    }

    private OffsetDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException ex) {
            throw new ApiException("Invalid timestamp: " + value);
        }
    }

    private void rateLimit(UUID raceId, HttpServletRequest request) {
        requestRateLimiter.checkPublicTracking(raceId.toString(), clientAddressResolver.resolve(request));
    }
}
