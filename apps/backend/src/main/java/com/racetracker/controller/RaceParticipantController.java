package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.participant.*;
import com.racetracker.exception.ApiException;
import com.racetracker.model.Participant;
import com.racetracker.service.ParticipantService;
import com.racetracker.service.AuditEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}/participants")
public class RaceParticipantController {

    private final ParticipantService participantService;
    private final AuditEventService auditEventService;

    public RaceParticipantController(ParticipantService participantService, AuditEventService auditEventService) {
        this.participantService = participantService;
        this.auditEventService = auditEventService;
    }

    @GetMapping("")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<ParticipantDto>> list(
        @PathVariable UUID raceId,
        @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(participantService.listByRace(raceId, parseStatus(status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ParticipantDto> get(@PathVariable UUID raceId, @PathVariable UUID id) {
        return ResponseEntity.ok(participantService.getByRaceAndId(raceId, id));
    }

    @PostMapping("")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ParticipantDto> create(@PathVariable UUID raceId, @Valid @RequestBody ParticipantCreateRequest request) {
        return ResponseEntity.ok(participantService.create(raceId, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ParticipantDto> update(@PathVariable UUID raceId, @PathVariable UUID id, @RequestBody ParticipantUpdateRequest request) {
        return ResponseEntity.ok(participantService.update(raceId, id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ApiMessageResponse> delete(@PathVariable UUID raceId, @PathVariable UUID id) {
        participantService.delete(raceId, id);
        return ResponseEntity.ok(new ApiMessageResponse("Participant deleted"));
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ParticipantDto> checkIn(@PathVariable UUID raceId, @PathVariable UUID id) {
        return ResponseEntity.ok(participantService.checkIn(raceId, id));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ParticipantDto> review(
        @PathVariable UUID raceId,
        @PathVariable UUID id,
        @Valid @RequestBody RegistrationApprovalRequest request,
        Authentication authentication
    ) {
        ParticipantDto dto = participantService.reviewRegistration(raceId, id, request, authentication.getName());
        auditEventService.recordForActor("REGISTRATION_" + request.getDecision().trim().toUpperCase(Locale.ROOT),
            authentication.getName(), "RACE_PARTICIPANT", id.toString(), "SUCCESS", Map.of());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/payment")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ParticipantDto> verifyPayment(
        @PathVariable UUID raceId,
        @PathVariable UUID id,
        @Valid @RequestBody PaymentVerifyRequest request,
        Authentication authentication
    ) {
        ParticipantDto dto = participantService.verifyPayment(raceId, id, request, authentication.getName());
        auditEventService.recordForActor("PAYMENT_VERIFIED", authentication.getName(), "RACE_PARTICIPANT",
            id.toString(), "SUCCESS", Map.of("status", request.getStatus().toUpperCase(Locale.ROOT)));
        return ResponseEntity.ok(dto);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ImportJobDto> importCsv(@PathVariable UUID raceId, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(participantService.importCsv(raceId, file));
    }

    @GetMapping("/import-jobs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<ImportJobDto>> listImportJobs(@PathVariable UUID raceId) {
        return ResponseEntity.ok(participantService.listImportJobs(raceId));
    }

    @GetMapping("/import-jobs/{jobId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ImportJobDto> getImportJob(@PathVariable UUID raceId, @PathVariable UUID jobId) {
        return ResponseEntity.ok(participantService.getImportJob(jobId));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<String> exportCsv(@PathVariable UUID raceId, Authentication authentication) {
        String content = participantService.exportCsv(raceId);
        auditEventService.recordForActor("PARTICIPANT_DATA_EXPORTED", authentication.getName(), "RACE", raceId.toString(),
            "SUCCESS", java.util.Map.of("format", "csv"));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=participants-" + raceId + ".csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(content);
    }

    @PostMapping("/register")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RegistrationDto> registerForRace(
        @PathVariable UUID raceId,
        @Valid @RequestBody RaceRegistrationRequest request
    ) {
        return ResponseEntity.ok(participantService.registerForRace(raceId, request));
    }

    private Participant.ParticipantStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return Participant.ParticipantStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid participant status: " + status);
        }
    }
}
