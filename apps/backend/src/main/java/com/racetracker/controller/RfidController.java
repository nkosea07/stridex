package com.racetracker.controller;

import com.racetracker.dto.rfid.RfidReadEventBatchRequest;
import com.racetracker.dto.rfid.RfidReadEventBatchResponse;
import com.racetracker.dto.rfid.RfidReaderCreateRequest;
import com.racetracker.dto.rfid.RfidReaderCreateResponse;
import com.racetracker.dto.rfid.RfidReaderDto;
import com.racetracker.dto.rfid.RfidReaderHeartbeatRequest;
import com.racetracker.dto.rfid.RfidRaceOverviewDto;
import com.racetracker.service.RfidIngestService;
import com.racetracker.service.RfidReaderService;
import com.racetracker.service.AuditEventService;
import com.racetracker.security.RaceAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class RfidController {

    static final String RFID_READER_KEY_HEADER = "X-RFID-Reader-Key";

    private final RfidReaderService rfidReaderService;
    private final RfidIngestService rfidIngestService;
    private final AuditEventService auditEventService;
    private final RaceAccessGuard raceAccessGuard;

    public RfidController(RfidReaderService rfidReaderService, RfidIngestService rfidIngestService,
                          AuditEventService auditEventService, RaceAccessGuard raceAccessGuard) {
        this.rfidReaderService = rfidReaderService;
        this.rfidIngestService = rfidIngestService;
        this.auditEventService = auditEventService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/rfid/readers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<RfidReaderCreateResponse> createReader(
        @PathVariable UUID raceId,
        @Valid @RequestBody RfidReaderCreateRequest request,
        Authentication authentication
    ) {
        raceAccessGuard.assertCanManage(raceId);
        RfidReaderCreateResponse created = rfidReaderService.create(raceId, request);
        auditEventService.recordForActor("RFID_READER_CREATED", authentication.getName(), "RFID_READER", created.getId().toString(),
            "SUCCESS", java.util.Map.of("raceId", raceId.toString(), "readerClass", String.valueOf(created.getReaderClass())));
        return ResponseEntity.ok(created);
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/rfid/readers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<List<RfidReaderDto>> listReaders(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(rfidReaderService.list(raceId));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/rfid/overview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<RfidRaceOverviewDto> overview(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(rfidReaderService.overview(raceId));
    }

    @PatchMapping("/v${racetracker.api.version}/races/{raceId}/rfid/readers/{readerId}/revoke")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<RfidReaderDto> revokeReader(@PathVariable UUID raceId, @PathVariable UUID readerId, Authentication authentication) {
        raceAccessGuard.assertCanManage(raceId);
        RfidReaderDto revoked = rfidReaderService.revoke(raceId, readerId);
        auditEventService.recordForActor("RFID_READER_REVOKED", authentication.getName(), "RFID_READER", readerId.toString(),
            "SUCCESS", java.util.Map.of("raceId", raceId.toString()));
        return ResponseEntity.ok(revoked);
    }

    @PostMapping("/v${racetracker.api.version}/rfid/read-events")
    public ResponseEntity<RfidReadEventBatchResponse> ingestReads(
        @RequestHeader(value = RFID_READER_KEY_HEADER, required = false) String apiKey,
        @Valid @RequestBody RfidReadEventBatchRequest request,
        HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(rfidIngestService.ingest(apiKey, request, resolveSourceIp(servletRequest)));
    }

    @PostMapping("/v${racetracker.api.version}/rfid/readers/heartbeat")
    public ResponseEntity<RfidReaderDto> heartbeat(
        @RequestHeader(value = RFID_READER_KEY_HEADER, required = false) String apiKey,
        @Valid @RequestBody RfidReaderHeartbeatRequest request,
        HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(rfidReaderService.heartbeat(apiKey, request, resolveSourceIp(servletRequest)));
    }

    private String resolveSourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
