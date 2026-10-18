package com.racetracker.controller;

import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.BibService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/races/{raceId}/bibs")
public class BibController {

    private final BibService bibService;
    private final RaceAccessGuard raceAccessGuard;

    public BibController(BibService bibService, RaceAccessGuard raceAccessGuard) {
        this.bibService = bibService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @GetMapping("/{participantId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<byte[]> getBib(@PathVariable UUID raceId, @PathVariable UUID participantId) {
        raceAccessGuard.assertCanManage(raceId);
        byte[] pdf = bibService.generateBibPdf(raceId, participantId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bib-" + participantId + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')")
    public ResponseEntity<byte[]> getBatchBibs(@PathVariable UUID raceId, @RequestBody List<UUID> participantIds) {
        raceAccessGuard.assertCanManage(raceId);
        byte[] pdf = bibService.generateBatchBibsPdf(raceId, participantIds);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bibs-batch-" + raceId + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }
}
