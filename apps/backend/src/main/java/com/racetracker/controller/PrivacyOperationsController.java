package com.racetracker.controller;

import com.racetracker.dto.privacy.PrivacyDeletionJobDto;
import com.racetracker.dto.privacy.PrivacyScopeVerificationRequest;
import com.racetracker.service.PrivacyObjectRemediationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class PrivacyOperationsController {

    private final PrivacyObjectRemediationService remediationService;

    public PrivacyOperationsController(PrivacyObjectRemediationService remediationService) {
        this.remediationService = remediationService;
    }

    @GetMapping("/v${racetracker.api.version}/admin/privacy/object-deletion-jobs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<PrivacyDeletionJobDto>> list(
        @RequestParam(required = false) String scope,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(remediationService.listBlockedJobs(scope, limit));
    }

    @PatchMapping("/v${racetracker.api.version}/admin/privacy/object-deletion-jobs/{jobId}/scope")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PrivacyDeletionJobDto> verifyScope(
        @PathVariable UUID jobId,
        @Valid @RequestBody PrivacyScopeVerificationRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(remediationService.verifyScope(jobId, request, authentication.getName()));
    }
}
