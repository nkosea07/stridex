package com.racetracker.controller;

import com.racetracker.dto.media.MediaConsentDto;
import com.racetracker.dto.media.MediaConsentRequest;
import com.racetracker.service.MediaConsentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class MediaConsentController {
    private final MediaConsentService mediaConsentService;

    public MediaConsentController(MediaConsentService mediaConsentService) {
        this.mediaConsentService = mediaConsentService;
    }

    @PutMapping("/v${racetracker.api.version}/participants/my-registrations/{participantId}/media-consent")
    @PreAuthorize("hasAuthority('CAP_ATHLETE')")
    public ResponseEntity<MediaConsentDto> update(@PathVariable UUID participantId,
                                                   @Valid @RequestBody MediaConsentRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.ok(mediaConsentService.update(participantId, authentication.getName(), request.consented()));
    }
}
