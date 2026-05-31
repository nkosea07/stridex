package com.racetracker.controller;

import com.racetracker.dto.participant.RegistrationDto;
import com.racetracker.service.ParticipantService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v${racetracker.api.version}/participants")
public class ParticipantController {

    private final ParticipantService participantService;

    public ParticipantController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @GetMapping("/my-registrations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RegistrationDto>> myRegistrations() {
        return ResponseEntity.ok(participantService.getMyRegistrations());
    }
}
