package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.result.RaceResultDto;
import com.racetracker.service.ResultService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ResultController {

    private final ResultService resultService;

    public ResultController(ResultService resultService) {
        this.resultService = resultService;
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')")
    public ResponseEntity<List<RaceResultDto>> listByRace(@PathVariable UUID raceId) {
        return ResponseEntity.ok(resultService.listByRace(raceId));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/results/{participantId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')")
    public ResponseEntity<RaceResultDto> getByParticipant(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId
    ) {
        return ResponseEntity.ok(resultService.getByRaceAndParticipant(raceId, participantId));
    }

    @GetMapping("/v${racetracker.api.version}/results/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RaceResultDto>> myResults() {
        return ResponseEntity.ok(resultService.getMyResults());
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/results/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ApiMessageResponse> publish(@PathVariable UUID raceId) {
        resultService.publish(raceId);
        return ResponseEntity.ok(new ApiMessageResponse("Results published"));
    }
}
