package com.racetracker.service.impl;

import com.racetracker.model.ImportJob;
import com.racetracker.model.Participant;
import com.racetracker.repository.ImportJobRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.service.ParticipantCredentialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ParticipantImportRowService {

    private final ImportJobRepository importJobRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantCredentialService credentialService;
    private final ParticipantIdentityService identityService;

    public ParticipantImportRowService(ImportJobRepository importJobRepository,
                                       ParticipantRepository participantRepository,
                                       ParticipantCredentialService credentialService,
                                       ParticipantIdentityService identityService) {
        this.importJobRepository = importJobRepository;
        this.participantRepository = participantRepository;
        this.credentialService = credentialService;
        this.identityService = identityService;
    }

    @Transactional
    public void importRow(UUID jobId, int attempt, int rowNumber, Participant participant) {
        ImportJob job = lockNextRow(jobId, attempt, rowNumber);
        identityService.assign(participant);
        if (participantRepository.existsByRaceIdAndEmailIgnoreCase(participant.getRace().getId(),
            participant.getEmail())
            || (participant.getBibNumber() != null
                && participantRepository.existsByBibNumber(participant.getBibNumber()))
            || (participant.getRfidTag() != null
                && participantRepository.existsByRfidTag(participant.getRfidTag()))) {
            throw new DuplicateValueException();
        }
        Participant saved = participantRepository.save(participant);
        credentialService.syncParticipantCredentials(saved, participant.getRfidTag(), null);
        job.setProcessedRows(rowNumber);
        job.setSuccessCount(count(job.getSuccessCount()) + 1);
        importJobRepository.save(job);
    }

    @Transactional
    public void skipRow(UUID jobId, int attempt, int rowNumber) {
        ImportJob job = lockNextRow(jobId, attempt, rowNumber);
        job.setProcessedRows(rowNumber);
        job.setErrorCount(count(job.getErrorCount()) + 1);
        importJobRepository.save(job);
    }

    private ImportJob lockNextRow(UUID jobId, int attempt, int rowNumber) {
        ImportJob job = importJobRepository.findByIdForUpdate(jobId)
            .orElseThrow(() -> new IllegalStateException("Import job missing"));
        if (job.getStatus() != ImportJob.JobStatus.PROCESSING
            || count(job.getAttempts()) != attempt
            || rowNumber != count(job.getProcessedRows()) + 1) {
            throw new IllegalStateException("Import row cursor changed");
        }
        return job;
    }

    private int count(Integer value) {
        return value == null ? 0 : value;
    }

    public static class DuplicateValueException extends RuntimeException {
    }
}
