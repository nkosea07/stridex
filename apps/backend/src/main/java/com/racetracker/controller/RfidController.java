package com.racetracker.controller;

import com.racetracker.dto.rfid.RfidReadEventBatchRequest;
import com.racetracker.dto.rfid.RfidReadEventBatchResponse;
import com.racetracker.dto.rfid.RfidReaderCreateRequest;
import com.racetracker.dto.rfid.RfidReaderCreateResponse;
import com.racetracker.dto.rfid.RfidReaderDto;
import com.racetracker.service.RfidIngestService;
import com.racetracker.service.RfidReaderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class RfidController {

    static final String RFID_READER_KEY_HEADER = "X-RFID-Reader-Key";

    private final RfidReaderService rfidReaderService;
    private final RfidIngestService rfidIngestService;

    public RfidController(RfidReaderService rfidReaderService, RfidIngestService rfidIngestService) {
        this.rfidReaderService = rfidReaderService;
        this.rfidIngestService = rfidIngestService;
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/rfid/readers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<RfidReaderCreateResponse> createReader(
        @PathVariable UUID raceId,
        @Valid @RequestBody RfidReaderCreateRequest request
    ) {
        return ResponseEntity.ok(rfidReaderService.create(raceId, request));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/rfid/readers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<List<RfidReaderDto>> listReaders(@PathVariable UUID raceId) {
        return ResponseEntity.ok(rfidReaderService.list(raceId));
    }

    @PatchMapping("/v${racetracker.api.version}/races/{raceId}/rfid/readers/{readerId}/revoke")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<RfidReaderDto> revokeReader(@PathVariable UUID raceId, @PathVariable UUID readerId) {
        return ResponseEntity.ok(rfidReaderService.revoke(raceId, readerId));
    }

    @PostMapping("/v${racetracker.api.version}/rfid/read-events")
    public ResponseEntity<RfidReadEventBatchResponse> ingestReads(
        @RequestHeader(value = RFID_READER_KEY_HEADER, required = false) String apiKey,
        @Valid @RequestBody RfidReadEventBatchRequest request,
        HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(rfidIngestService.ingest(apiKey, request, resolveSourceIp(servletRequest)));
    }

    private String resolveSourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
