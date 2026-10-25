package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.participant.*;
import com.racetracker.exception.ApiException;
import com.racetracker.model.Participant;
import com.racetracker.service.ParticipantService;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.ExportedFile;
import com.racetracker.security.RaceAccessGuard;
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
    private final RaceAccessGuard raceAccessGuard;

    public RaceParticipantController(ParticipantService participantService, AuditEventService auditEventService,
                                     RaceAccessGuard raceAccessGuard) {
        this.participantService = participantService;
        this.auditEventService = auditEventService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @GetMapping("")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<ParticipantDto>> list(
        @PathVariable UUID raceId,
        @RequestParam(required = false) String status,
        Authentication authentication
    ) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(participantService.listByRace(raceId, parseStatus(status)).stream()
            .map(participant -> redactFinancialFields(participant, authentication))
            .toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ParticipantDto> get(@PathVariable UUID raceId, @PathVariable UUID id,
                                              Authentication authentication) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(redactFinancialFields(
            participantService.getByRaceAndId(raceId, id), authentication));
    }

    @PostMapping("")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ParticipantDto> create(@PathVariable UUID raceId, @Valid @RequestBody ParticipantCreateRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(participantService.create(raceId, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ParticipantDto> update(@PathVariable UUID raceId, @PathVariable UUID id, @RequestBody ParticipantUpdateRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(participantService.update(raceId, id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ApiMessageResponse> delete(@PathVariable UUID raceId, @PathVariable UUID id) {
        raceAccessGuard.assertCanManage(raceId);
        participantService.delete(raceId, id);
        return ResponseEntity.ok(new ApiMessageResponse("Participant deleted"));
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ParticipantDto> checkIn(@PathVariable UUID raceId, @PathVariable UUID id) {
        raceAccessGuard.assertCanPerformOperationalAction(raceId);
        return ResponseEntity.ok(participantService.checkIn(raceId, id));
    }

    @PostMapping("/qr-check-in")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ParticipantDto> qrCheckIn(@PathVariable UUID raceId,
                                                    @Valid @RequestBody QrCheckInRequest request,
                                                    Authentication authentication) {
        raceAccessGuard.assertCanPerformOperationalAction(raceId);
        ParticipantDto dto = participantService.checkInByQr(raceId, request.code());
        auditEventService.recordForActor("PARTICIPANT_QR_CHECKED_IN", authentication.getName(),
            "RACE_PARTICIPANT", dto.getId().toString(), "SUCCESS", Map.of("raceId", raceId.toString()));
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ParticipantDto> review(
        @PathVariable UUID raceId,
        @PathVariable UUID id,
        @Valid @RequestBody RegistrationApprovalRequest request,
        Authentication authentication
    ) {
        raceAccessGuard.assertCanManage(raceId);
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
        raceAccessGuard.assertCanManage(raceId);
        ParticipantDto dto = participantService.verifyPayment(raceId, id, request, authentication.getName());
        auditEventService.recordForActor("PAYMENT_VERIFIED", authentication.getName(), "RACE_PARTICIPANT",
            id.toString(), "SUCCESS", Map.of("status", request.getStatus().toUpperCase(Locale.ROOT)));
        return ResponseEntity.ok(dto);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ImportJobDto> importCsv(@PathVariable UUID raceId, @RequestPart("file") MultipartFile file) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(participantService.importCsv(raceId, file));
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<com.racetracker.dto.participant.ImportPreviewDto> previewImport(
        @PathVariable UUID raceId,
        @RequestPart("file") MultipartFile file
    ) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(participantService.previewImport(raceId, file));
    }

    @GetMapping("/import-jobs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<List<ImportJobDto>> listImportJobs(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(participantService.listImportJobs(raceId));
    }

    @GetMapping("/import-jobs/{jobId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')")
    public ResponseEntity<ImportJobDto> getImportJob(@PathVariable UUID raceId, @PathVariable UUID jobId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return ResponseEntity.ok(participantService.getImportJob(raceId, jobId));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<byte[]> exportCsv(@PathVariable UUID raceId,
                                            @RequestParam(defaultValue = "csv") String format,
                                            Authentication authentication) {
        raceAccessGuard.assertCanManage(raceId);
        String normalizedFormat = format.trim().toLowerCase(Locale.ROOT);
        ExportedFile file = participantService.export(raceId, normalizedFormat);
        auditEventService.recordForActor("PARTICIPANT_DATA_EXPORTED", authentication.getName(), "RACE", raceId.toString(),
            "SUCCESS", Map.of("format", normalizedFormat));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
            .contentType(MediaType.parseMediaType(file.contentType()))
            .body(file.content());
    }

    @PostMapping("/register")
    @PreAuthorize("hasAuthority('CAP_ATHLETE')")
    public ResponseEntity<RegistrationDto> registerForRace(
        @PathVariable UUID raceId,
        @Valid @RequestBody RaceRegistrationRequest request
    ) {
        return ResponseEntity.ok(participantService.registerForRace(raceId, request));
    }

    private ParticipantDto redactFinancialFields(ParticipantDto participant, Authentication authentication) {
        if (isFinancialRole(authentication)) {
            return participant;
        }
        participant.setPaymentReference(null);
        participant.setPaymentAmount(null);
        participant.setPaymentVerifiedAt(null);
        return participant;
    }

    private boolean isFinancialRole(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .anyMatch(authority -> authority.equals("ROLE_SUPER_ADMIN") || authority.equals("ROLE_RACE_ADMIN"));
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
