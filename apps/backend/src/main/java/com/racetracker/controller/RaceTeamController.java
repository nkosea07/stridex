package com.racetracker.controller;

import com.racetracker.dto.participant.TeamCreateRequest;
import com.racetracker.dto.participant.TeamDto;
import com.racetracker.service.ParticipantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}/teams")
public class RaceTeamController {

    private final ParticipantService participantService;

    public RaceTeamController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @GetMapping("")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<TeamDto>> list(@PathVariable UUID raceId) {
        return ResponseEntity.ok(participantService.listTeams(raceId));
    }

    @PostMapping("")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<TeamDto> create(@PathVariable UUID raceId, @Valid @RequestBody TeamCreateRequest request) {
        return ResponseEntity.ok(participantService.createTeam(raceId, request));
    }
}
