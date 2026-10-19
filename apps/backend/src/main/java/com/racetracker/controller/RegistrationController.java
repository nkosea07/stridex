package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.service.ParticipantService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/registrations")
public class RegistrationController {

    private final ParticipantService participantService;

    public RegistrationController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @DeleteMapping("/{registrationId}")
    @PreAuthorize("hasAuthority('CAP_ATHLETE')")
    public ResponseEntity<ApiMessageResponse> cancelRegistration(@PathVariable UUID registrationId) {
        participantService.cancelRegistration(registrationId);
        return ResponseEntity.ok(new ApiMessageResponse("Registration cancelled"));
    }
}
