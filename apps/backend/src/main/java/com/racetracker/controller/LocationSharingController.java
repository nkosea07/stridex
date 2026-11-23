package com.racetracker.controller;

import com.racetracker.dto.participant.LocationSharingDto;
import com.racetracker.dto.participant.LocationSharingRequest;
import com.racetracker.service.LocationSharingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class LocationSharingController {
    private final LocationSharingService locationSharingService;

    public LocationSharingController(LocationSharingService locationSharingService) {
        this.locationSharingService = locationSharingService;
    }

    @PutMapping("/v${racetracker.api.version}/participants/my-registrations/{participantId}/location-sharing")
    @PreAuthorize("hasAuthority('CAP_ATHLETE')")
    public ResponseEntity<LocationSharingDto> update(@PathVariable UUID participantId,
                                                     @Valid @RequestBody LocationSharingRequest request,
                                                     Authentication authentication) {
        return ResponseEntity.ok(locationSharingService.update(participantId, authentication.getName(), request.enabled()));
    }
}
