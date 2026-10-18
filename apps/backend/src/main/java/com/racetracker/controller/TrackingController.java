package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.tracking.*;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.TrackingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class TrackingController {

    private final TrackingService trackingService;
    private final RaceAccessGuard raceAccessGuard;

    public TrackingController(TrackingService trackingService, RaceAccessGuard raceAccessGuard) {
        this.trackingService = trackingService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @PostMapping("/v${racetracker.api.version}/tracking/{raceId}/location")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE')")
    public ResponseEntity<ApiMessageResponse> sendLocation(@PathVariable UUID raceId, @Valid @RequestBody GpsLocationRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        trackingService.recordLocation(raceId, request);
        return ResponseEntity.ok(new ApiMessageResponse("Location recorded"));
    }

    @PostMapping("/v${racetracker.api.version}/tracking/{raceId}/locations")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE')")
    public ResponseEntity<ApiMessageResponse> sendLocationsBatch(@PathVariable UUID raceId, @Valid @RequestBody GpsLocationBatchRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        trackingService.recordLocationsBatch(raceId, request);
        return ResponseEntity.ok(new ApiMessageResponse("Locations recorded"));
    }

    @PostMapping("/v${racetracker.api.version}/tracking/{raceId}/checkpoint")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE')")
    public ResponseEntity<ApiMessageResponse> recordCheckpoint(@PathVariable UUID raceId, @Valid @RequestBody TrackingCheckpointRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        trackingService.recordCheckpoint(raceId, request);
        return ResponseEntity.ok(new ApiMessageResponse("Checkpoint recorded"));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/tracking/live")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<LiveTrackingResponse>> getLiveData(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(trackingService.getLiveData(raceId));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/tracking/{participantId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ParticipantTrackResponse> getParticipantTrack(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId
    ) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(trackingService.getParticipantTrack(raceId, participantId));
    }
}
