package com.racetracker.service;

import com.racetracker.dto.privacy.PrivacyDeletionJobDto;
import com.racetracker.dto.privacy.PrivacyScopeVerificationRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.model.PrivacyObjectDeletionJob;
import com.racetracker.repository.PrivacyObjectDeletionJobRepository;
import com.racetracker.service.impl.PrivacyObjectRemediationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivacyObjectRemediationServiceImplTest {

    @Mock PrivacyObjectDeletionJobRepository jobRepository;
    @Mock AuditEventService auditEventService;

    private PrivacyObjectRemediationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PrivacyObjectRemediationServiceImpl(jobRepository, auditEventService);
    }

    @Test
    void listsUnverifiedLegacyJobsByDefault() {
        PrivacyObjectDeletionJob job = job(PrivacyObjectDeletionJob.Status.PENDING);
        when(jobRepository.findByStorageScopeAndStatusNotOrderByCreatedAtAsc(
            "UNVERIFIED_LEGACY", PrivacyObjectDeletionJob.Status.COMPLETED, PageRequest.of(0, 100)))
            .thenReturn(List.of(job));

        List<PrivacyDeletionJobDto> result = service.listBlockedJobs(null, 100);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.storageScope()).isEqualTo("UNVERIFIED_LEGACY");
            assertThat(dto.storageKey()).isEqualTo("original.jpg");
        });
    }

    @Test
    void verifiedScopeResetsTheJobAndRecordsAnAuditEvent() {
        PrivacyObjectDeletionJob job = job(PrivacyObjectDeletionJob.Status.PENDING);
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        PrivacyDeletionJobDto result = service.verifyScope(job.getId(),
            new PrivacyScopeVerificationRequest("local|/verified/legacy", "Deployment history confirmed"), "admin@example.com");

        assertThat(result.storageScope()).isEqualTo("local|/verified/legacy");
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getStatus()).isEqualTo(PrivacyObjectDeletionJob.Status.PENDING);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditEventService).recordForActor(eq("PRIVACY_OBJECT_SCOPE_VERIFIED"),
            eq("admin@example.com"), eq("PRIVACY_DELETION_JOB"), eq(job.getId().toString()),
            eq("SUCCESS"), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("verifiedScope", "local|/verified/legacy");
    }

    @Test
    void rejectsInvalidScopeAndActiveLease() {
        assertThatThrownBy(() -> service.verifyScope(UUID.randomUUID(),
            new PrivacyScopeVerificationRequest("https://bucket.example", "reason"), "admin@example.com"))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("PRIVACY_SCOPE_INVALID");

        PrivacyObjectDeletionJob processing = job(PrivacyObjectDeletionJob.Status.PROCESSING);
        when(jobRepository.findByIdForUpdate(processing.getId())).thenReturn(Optional.of(processing));
        assertThatThrownBy(() -> service.verifyScope(processing.getId(),
            new PrivacyScopeVerificationRequest("local|/verified/legacy", "reason"), "admin@example.com"))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("PRIVACY_JOB_PROCESSING");
    }

    private PrivacyObjectDeletionJob job(PrivacyObjectDeletionJob.Status status) {
        PrivacyObjectDeletionJob job = new PrivacyObjectDeletionJob();
        job.setId(UUID.randomUUID());
        job.setUserId(UUID.randomUUID());
        job.setStorageKey("original.jpg");
        job.setStorageScope("UNVERIFIED_LEGACY");
        job.setStatus(status);
        job.setAttempts(3);
        job.setCreatedAt(OffsetDateTime.now().minusDays(1));
        job.setNextAttemptAt(OffsetDateTime.now().plusHours(1));
        return job;
    }
}
