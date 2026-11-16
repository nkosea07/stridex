package com.racetracker.controller;

import com.racetracker.dto.tracking.SelfScanClaimDto;
import com.racetracker.dto.tracking.SelfScanDecisionRequest;
import com.racetracker.service.SelfScanService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}")
public class SelfScanController {

    private final SelfScanService selfScanService;

    public SelfScanController(SelfScanService selfScanService) {
        this.selfScanService = selfScanService;
    }

    @GetMapping(value = "/checkpoints/{checkpointId}/self-scan-qr", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<byte[]> selfScanQr(@PathVariable UUID raceId, @PathVariable UUID checkpointId) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.IMAGE_PNG)
            .body(selfScanService.selfScanQrPng(raceId, checkpointId));
    }

    @PostMapping("/checkpoints/{checkpointId}/self-scan-qr/rotate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<Void> rotateSelfScanCode(@PathVariable UUID raceId, @PathVariable UUID checkpointId) {
        selfScanService.rotateSelfScanCode(raceId, checkpointId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/self-scan-claims")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<List<SelfScanClaimDto>> openClaims(@PathVariable UUID raceId) {
        return ResponseEntity.ok(selfScanService.openClaims(raceId));
    }

    @PostMapping("/self-scan-claims/{claimId}/decision")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<SelfScanClaimDto> decide(
        @PathVariable UUID raceId,
        @PathVariable UUID claimId,
        @Valid @RequestBody SelfScanDecisionRequest request
    ) {
        return ResponseEntity.ok(selfScanService.decide(raceId, claimId, request));
    }
}
