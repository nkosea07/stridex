package com.racetracker.service.impl;

import com.racetracker.dto.privacy.PrivacyDeletionJobDto;
import com.racetracker.dto.privacy.PrivacyScopeVerificationRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.PrivacyObjectDeletionJob;
import com.racetracker.repository.PrivacyObjectDeletionJobRepository;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.PrivacyObjectRemediationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PrivacyObjectRemediationServiceImpl implements PrivacyObjectRemediationService {

    private static final String UNVERIFIED_SCOPE = "UNVERIFIED_LEGACY";
    private static final Pattern VERIFIED_SCOPE = Pattern.compile(
        "^(local\\|.+|aws-s3\\|[^|]+\\|[^|]+\\|.*|minio\\|[^|]+\\|[^|]+\\|.*)$");

    private final PrivacyObjectDeletionJobRepository jobRepository;
    private final AuditEventService auditEventService;

    public PrivacyObjectRemediationServiceImpl(PrivacyObjectDeletionJobRepository jobRepository,
                                               AuditEventService auditEventService) {
        this.jobRepository = jobRepository;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrivacyDeletionJobDto> listBlockedJobs(String scope, int limit) {
        String requestedScope = scope == null || scope.isBlank() ? UNVERIFIED_SCOPE : scope.trim();
        int boundedLimit = Math.min(Math.max(limit, 1), 200);
        return jobRepository.findByStorageScopeAndStatusNotOrderByCreatedAtAsc(
                requestedScope, PrivacyObjectDeletionJob.Status.COMPLETED, PageRequest.of(0, boundedLimit))
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional
    public PrivacyDeletionJobDto verifyScope(UUID jobId, PrivacyScopeVerificationRequest request,
                                             String actorEmail) {
        String scope = request.scope() == null ? "" : request.scope().trim();
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (!VERIFIED_SCOPE.matcher(scope).matches()) {
            throw new ApiException("Verified storage scope must be a local, aws-s3, or minio scope",
                "PRIVACY_SCOPE_INVALID");
        }
        if (reason.isBlank()) {
            throw new ApiException("A verification reason is required", "PRIVACY_SCOPE_REASON_REQUIRED");
        }
        PrivacyObjectDeletionJob job = jobRepository.findByIdForUpdate(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Privacy deletion job not found"));
        if (job.getStatus() == PrivacyObjectDeletionJob.Status.COMPLETED) {
            throw new ConflictException("Completed privacy deletion jobs cannot be re-scoped",
                "PRIVACY_JOB_COMPLETED");
        }
        if (job.getStatus() == PrivacyObjectDeletionJob.Status.PROCESSING) {
            throw new ConflictException("Wait for the active deletion lease before changing scope",
                "PRIVACY_JOB_PROCESSING");
        }
        if (!UNVERIFIED_SCOPE.equals(job.getStorageScope())) {
            throw new ConflictException("Only unverified legacy jobs can be re-scoped",
                "PRIVACY_SCOPE_ALREADY_VERIFIED");
        }
        String previousScope = job.getStorageScope();
        job.setStorageScope(scope);
        job.setAttempts(0);
        job.setNextAttemptAt(OffsetDateTime.now());
        job.setStatus(PrivacyObjectDeletionJob.Status.PENDING);
        auditEventService.recordForActor("PRIVACY_OBJECT_SCOPE_VERIFIED", actorEmail, "PRIVACY_DELETION_JOB",
            jobId.toString(), "SUCCESS", Map.of(
                "previousScope", previousScope,
                "verifiedScope", scope,
                "reason", reason));
        return toDto(job);
    }

    private PrivacyDeletionJobDto toDto(PrivacyObjectDeletionJob job) {
        return new PrivacyDeletionJobDto(job.getId(), job.getUserId(), job.getStorageKey(),
            job.getStorageScope(), job.getStatus().name(), job.getAttempts(),
            job.getNextAttemptAt(), job.getCreatedAt());
    }
}
