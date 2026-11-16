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

    static final int MAX_RECORDED_ROW_ERRORS = 200;
    private static final com.fasterxml.jackson.databind.ObjectMapper ROW_ERRORS = new com.fasterxml.jackson.databind.ObjectMapper();

    private final com.racetracker.repository.RaceRepository raceRepository;

    public ParticipantImportRowService(ImportJobRepository importJobRepository,
                                       ParticipantRepository participantRepository,
                                       ParticipantCredentialService credentialService,
                                       ParticipantIdentityService identityService,
                                       com.racetracker.repository.RaceRepository raceRepository) {
        this.importJobRepository = importJobRepository;
        this.participantRepository = participantRepository;
        this.credentialService = credentialService;
        this.identityService = identityService;
        this.raceRepository = raceRepository;
    }

    @Transactional
    public void importRow(UUID jobId, int attempt, int rowNumber, Participant participant) {
        ImportJob job = lockNextRow(jobId, attempt, rowNumber);
        raceRepository.findByIdForUpdate(participant.getRace().getId())
            .orElseThrow(() -> new IllegalStateException("Import race missing"));
        identityService.assign(participant);
        if (participantRepository.existsByRaceIdAndEmailIgnoreCase(participant.getRace().getId(),
            participant.getEmail())) {
            throw new DuplicateValueException("Duplicate email");
        }
        UUID raceId = participant.getRace().getId();
        if (participant.getBibNumber() != null && participantRepository.existsByRaceIdAndBibNumber(raceId, participant.getBibNumber())) {
            throw new DuplicateValueException("Duplicate bibNumber");
        }
        if (participant.getRfidTag() != null && participantRepository.existsByRaceIdAndRfidTag(raceId, participant.getRfidTag())) {
            throw new DuplicateValueException("Duplicate rfidTag");
        }
        Participant saved = participantRepository.save(participant);
        credentialService.syncParticipantCredentials(saved, participant.getRfidTag(), null);
        job.setProcessedRows(rowNumber);
        job.setSuccessCount(count(job.getSuccessCount()) + 1);
        importJobRepository.save(job);
    }

    @Transactional
    public void skipRow(UUID jobId, int attempt, int rowNumber, String reason) {
        ImportJob job = lockNextRow(jobId, attempt, rowNumber);
        job.setProcessedRows(rowNumber);
        job.setErrorCount(count(job.getErrorCount()) + 1);
        job.setRowErrors(appendRowError(job.getRowErrors(), rowNumber, reason));
        importJobRepository.save(job);
    }

    static String appendRowError(String existing, int rowNumber, String reason) {
        try {
            java.util.List<java.util.Map<String, Object>> errors = existing == null || existing.isBlank()
                ? new java.util.ArrayList<>()
                : ROW_ERRORS.readValue(existing, new com.fasterxml.jackson.core.type.TypeReference<
                    java.util.List<java.util.Map<String, Object>>>() { });
            if (errors.size() >= MAX_RECORDED_ROW_ERRORS) {
                return existing;
            }
            java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("row", rowNumber);
            entry.put("reason", reason == null || reason.isBlank() ? "Invalid row" : reason);
            errors.add(entry);
            return ROW_ERRORS.writeValueAsString(errors);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return existing;
        }
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
        public DuplicateValueException(String reason) {
            super(reason);
        }
    }
}
