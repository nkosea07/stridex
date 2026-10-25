package com.racetracker.service.impl;

import com.racetracker.model.ImportJob;
import com.racetracker.repository.ImportJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class ParticipantImportJobService {

    private static final int MAX_ATTEMPTS = 3;

    private final ImportJobRepository repository;

    public ParticipantImportJobService(ImportJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<Claim> claim(UUID jobId, OffsetDateTime staleBefore) {
        Optional<ImportJob> candidate = repository.findByIdForUpdate(jobId);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        ImportJob job = candidate.get();
        if (job.getSourcePath() == null || job.getSourcePath().isBlank()
            || (job.getStatus() != ImportJob.JobStatus.PENDING
                && (job.getStatus() != ImportJob.JobStatus.PROCESSING
                    || (job.getUpdatedAt() != null && job.getUpdatedAt().isAfter(staleBefore))))) {
            return Optional.empty();
        }
        if (count(job.getAttempts()) >= MAX_ATTEMPTS) {
            job.setStatus(ImportJob.JobStatus.FAILED);
            job.setCompletedAt(OffsetDateTime.now());
            job.setErrorMessage("Import retry limit exhausted");
            repository.saveAndFlush(job);
            return Optional.empty();
        }
        int attempt = count(job.getAttempts()) + 1;
        job.setAttempts(attempt);
        job.setStatus(ImportJob.JobStatus.PROCESSING);
        job.setStartedAt(job.getStartedAt() == null ? OffsetDateTime.now() : job.getStartedAt());
        job.setCompletedAt(null);
        job.setErrorMessage(null);
        repository.saveAndFlush(job);
        return Optional.of(new Claim(job, attempt));
    }

    @Transactional
    public boolean setTotalRows(UUID jobId, int attempt, int totalRows) {
        ImportJob job = ownedJob(jobId, attempt);
        if (job == null) {
            return false;
        }
        if (totalRows < count(job.getProcessedRows())) {
            throw new IllegalStateException("Import source has fewer rows than the committed cursor");
        }
        job.setTotalRows(totalRows);
        repository.save(job);
        return true;
    }

    @Transactional
    public boolean complete(UUID jobId, int attempt) {
        ImportJob job = ownedJob(jobId, attempt);
        if (job == null) {
            return false;
        }
        if (count(job.getProcessedRows()) != count(job.getTotalRows())) {
            throw new IllegalStateException("Import row count is incomplete");
        }
        job.setStatus(ImportJob.JobStatus.COMPLETED);
        job.setCompletedAt(OffsetDateTime.now());
        repository.save(job);
        return true;
    }

    @Transactional
    public boolean fail(UUID jobId, int attempt, Exception failure) {
        ImportJob job = ownedJob(jobId, attempt);
        if (job == null) {
            return false;
        }
        if (attempt >= MAX_ATTEMPTS) {
            job.setStatus(ImportJob.JobStatus.FAILED);
            job.setCompletedAt(OffsetDateTime.now());
        } else {
            job.setStatus(ImportJob.JobStatus.PENDING);
        }
        job.setErrorMessage(failure.getClass().getSimpleName());
        repository.save(job);
        return true;
    }

    private ImportJob ownedJob(UUID jobId, int attempt) {
        return repository.findByIdForUpdate(jobId)
            .filter(job -> job.getStatus() == ImportJob.JobStatus.PROCESSING
                && count(job.getAttempts()) == attempt)
            .orElse(null);
    }

    private int count(Integer value) {
        return value == null ? 0 : value;
    }

    public record Claim(ImportJob job, int attempt) {
    }
}
