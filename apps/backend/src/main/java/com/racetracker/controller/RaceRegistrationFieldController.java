package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.participant.RaceRegistrationFieldDto;
import com.racetracker.dto.participant.RegistrationFieldRequest;
import com.racetracker.service.ParticipantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}/registration-fields")
public class RaceRegistrationFieldController {

    private final ParticipantService participantService;

    public RaceRegistrationFieldController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @GetMapping("")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<List<RaceRegistrationFieldDto>> list(@PathVariable UUID raceId) {
        return ResponseEntity.ok(participantService.listRegistrationFields(raceId, false));
    }

    @PostMapping("")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceRegistrationFieldDto> create(
        @PathVariable UUID raceId,
        @Valid @RequestBody RegistrationFieldRequest request
    ) {
        return ResponseEntity.ok(participantService.createRegistrationField(raceId, request));
    }

    @PutMapping("/{fieldId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceRegistrationFieldDto> update(
        @PathVariable UUID raceId,
        @PathVariable UUID fieldId,
        @Valid @RequestBody RegistrationFieldRequest request
    ) {
        return ResponseEntity.ok(participantService.updateRegistrationField(raceId, fieldId, request));
    }

    @DeleteMapping("/{fieldId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ApiMessageResponse> delete(@PathVariable UUID raceId, @PathVariable UUID fieldId) {
        participantService.deleteRegistrationField(raceId, fieldId);
        return ResponseEntity.ok(new ApiMessageResponse("Registration field deleted"));
    }
}
