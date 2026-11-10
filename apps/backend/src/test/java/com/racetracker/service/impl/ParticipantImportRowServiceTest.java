package com.racetracker.service.impl;

import com.racetracker.model.ImportJob;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.ImportJobRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.service.ParticipantCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantImportRowServiceTest {

    @Mock com.racetracker.repository.RaceRepository raceRepository;
    @Mock ImportJobRepository jobRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock ParticipantCredentialService credentialService;
    @Mock ParticipantIdentityService identityService;

    ParticipantImportRowService service;
    UUID jobId = UUID.randomUUID();
    Race race;
    ImportJob job;
    Participant participant;

    @BeforeEach
    void setUp() {
        service = new ParticipantImportRowService(jobRepository, participantRepository, credentialService,
            identityService, raceRepository);
        race = new Race();
        race.setId(UUID.randomUUID());
        job = new ImportJob();
        job.setStatus(ImportJob.JobStatus.PROCESSING);
        job.setAttempts(1);
        job.setProcessedRows(0);
        job.setSuccessCount(0);
        job.setErrorCount(0);
        participant = new Participant();
        participant.setRace(race);
        participant.setEmail("runner@example.com");
        participant.setRfidTag("TAG-1");
        org.mockito.Mockito.lenient().when(raceRepository.findByIdForUpdate(race.getId()))
            .thenReturn(java.util.Optional.of(race));
    }

    @Test
    void skippedRowsRecordTheirReasonUpToTheCap() {
        String errors = null;
        for (int row = 1; row <= ParticipantImportRowService.MAX_RECORDED_ROW_ERRORS + 5; row++) {
            errors = ParticipantImportRowService.appendRowError(errors, row, "Invalid email");
        }
        assertThat(errors).startsWith("[{\"row\":1,\"reason\":\"Invalid email\"}");
        assertThat(errors.split("\"row\"").length - 1).isEqualTo(ParticipantImportRowService.MAX_RECORDED_ROW_ERRORS);
    }

    @Test
    void successfulRowAdvancesCursorAfterCredentialSync() throws Exception {
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(participantRepository.save(participant)).thenReturn(participant);

        service.importRow(jobId, 1, 1, participant);

        InOrder order = inOrder(jobRepository, identityService, participantRepository, credentialService);
        order.verify(jobRepository).findByIdForUpdate(jobId);
        order.verify(identityService).assign(participant);
        order.verify(participantRepository).save(participant);
        order.verify(credentialService).syncParticipantCredentials(participant, "TAG-1", null);
        order.verify(jobRepository).save(job);
        assertThat(job.getProcessedRows()).isEqualTo(1);
        assertThat(job.getSuccessCount()).isEqualTo(1);
        assertThat(ParticipantImportRowService.class.getMethod("importRow", UUID.class, int.class,
            int.class, Participant.class).isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void duplicateEmailLeavesCursorForSeparateSkipTransaction() {
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(participantRepository.existsByRaceIdAndEmailIgnoreCase(race.getId(), participant.getEmail()))
            .thenReturn(true);

        assertThatThrownBy(() -> service.importRow(jobId, 1, 1, participant))
            .isInstanceOf(ParticipantImportRowService.DuplicateValueException.class);
        assertThat(job.getProcessedRows()).isZero();
        verify(participantRepository, never()).save(any());
        verify(jobRepository, never()).save(any());

        service.skipRow(jobId, 1, 1, "Invalid email");
        assertThat(job.getProcessedRows()).isEqualTo(1);
        assertThat(job.getErrorCount()).isEqualTo(1);
        verify(jobRepository).save(job);
    }

    @Test
    void credentialFailureDoesNotAdvanceCursor() {
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(participantRepository.save(participant)).thenReturn(participant);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"))
            .when(credentialService).syncParticipantCredentials(participant, "TAG-1", null);

        assertThatThrownBy(() -> service.importRow(jobId, 1, 1, participant))
            .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(job.getProcessedRows()).isZero();
        verify(jobRepository, never()).save(any());
    }

    @Test
    void duplicateCredentialEscapesRowTransactionForSeparateSkip() {
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(participantRepository.save(participant)).thenReturn(participant);
        org.mockito.Mockito.doThrow(new com.racetracker.exception.ConflictException("duplicate credential"))
            .when(credentialService).syncParticipantCredentials(participant, "TAG-1", null);

        assertThatThrownBy(() -> service.importRow(jobId, 1, 1, participant))
            .isInstanceOf(com.racetracker.exception.ConflictException.class);
        assertThat(job.getProcessedRows()).isZero();
        verify(jobRepository, never()).save(any());
    }

    @Test
    void cursorMismatchCannotDoubleCountRow() {
        job.setProcessedRows(1);
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.importRow(jobId, 1, 1, participant))
            .isInstanceOf(IllegalStateException.class);
        verify(identityService, never()).assign(any());
    }
}
