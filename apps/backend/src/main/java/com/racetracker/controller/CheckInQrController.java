package com.racetracker.controller;

import com.racetracker.dto.participant.CheckInQrDto;
import com.racetracker.service.ParticipantService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class CheckInQrController {
    private final ParticipantService participantService;

    public CheckInQrController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @GetMapping("/v${racetracker.api.version}/participants/my-registrations/{participantId}/check-in-qr")
    @PreAuthorize("hasAuthority('CAP_ATHLETE')")
    public ResponseEntity<CheckInQrDto> checkInQr(@PathVariable UUID participantId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(participantService.getMyCheckInQr(participantId));
    }
}
