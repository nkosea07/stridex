package com.racetracker.service.impl;

import com.racetracker.model.ImportJob;
import com.racetracker.exception.ConflictException;
import com.racetracker.dto.participant.ImportPreviewDto;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.ImportJobRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class ParticipantImportProcessorTest {

    @Mock ImportJobRepository importJobRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock RaceCategoryRepository raceCategoryRepository;
    @Mock ParticipantImportRowService rowService;

    @TempDir Path tempDir;

    private ParticipantImportProcessor processor;
    private ParticipantImportJobService jobService;
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jobService = new ParticipantImportJobService(importJobRepository);
        processor = new ParticipantImportProcessor(importJobRepository, participantRepository,
            raceCategoryRepository, rowService, jobService);
    }

    private ImportJob job(Path csv) {
        Race race = new Race();
        race.setId(UUID.randomUUID());
        race.setRegistrationFee(BigDecimal.ZERO);
        ImportJob job = ImportJob.builder()
            .race(race)
            .fileName("participants.csv")
            .sourcePath(csv.toString())
            .status(ImportJob.JobStatus.PENDING)
            .build();
        job.setId(jobId);
        job.setAttempts(0);
        job.setProcessedRows(0);
        job.setSuccessCount(0);
        job.setErrorCount(0);
        return job;
    }

    @Test
    void runJobImportsRowsAndMarksCompleted() throws Exception {
        Path csv = tempDir.resolve("participants.csv");
        Files.writeString(csv, "email,firstName,lastName,phone\n"
            + "a@example.com,Ada,Runner,0700\n"
            + "b@example.com,Bea,Fast,0711\n", StandardCharsets.UTF_8);

        ImportJob job = job(csv);
        when(importJobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        doAnswer(inv -> {
            job.setProcessedRows(inv.getArgument(2));
            job.setSuccessCount(job.getSuccessCount() + 1);
            return null;
        }).when(rowService).importRow(eq(jobId), eq(1), anyInt(), any(Participant.class));

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.COMPLETED);
        assertThat(job.getSuccessCount()).isEqualTo(2);
        assertThat(job.getProcessedRows()).isEqualTo(2);
        assertThat(job.getTotalRows()).isEqualTo(2);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(Files.exists(csv)).isFalse();
    }

    @Test
    void runJobSkipsDuplicateEmails() throws Exception {
        Path csv = tempDir.resolve("dupes.csv");
        Files.writeString(csv, "email,firstName,lastName\n"
            + "existing@example.com,Ada,Runner\n"
            + "new@example.com,Bea,Fast\n", StandardCharsets.UTF_8);

        ImportJob job = job(csv);
        when(importJobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new ParticipantImportRowService.DuplicateValueException())
            .when(rowService).importRow(eq(jobId), eq(1), eq(1), any(Participant.class));
        doAnswer(inv -> {
            job.setProcessedRows(inv.getArgument(2));
            job.setErrorCount(job.getErrorCount() + 1);
            return null;
        }).when(rowService).skipRow(eq(jobId), eq(1), eq(1));
        doAnswer(inv -> {
            job.setProcessedRows(inv.getArgument(2));
            job.setSuccessCount(job.getSuccessCount() + 1);
            return null;
        }).when(rowService).importRow(eq(jobId), eq(1), eq(2), any(Participant.class));

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.COMPLETED);
        assertThat(job.getSuccessCount()).isEqualTo(1);
        assertThat(job.getErrorCount()).isEqualTo(1);
        assertThat(job.getProcessedRows()).isEqualTo(2);
    }

    @Test
    void credentialConflictIsCountedAfterRowRollback() throws Exception {
        Path csv = tempDir.resolve("credential-duplicate.csv");
        Files.writeString(csv, "email,firstName,lastName,rfidTag\n"
            + "new@example.com,Ada,Runner,TAG-1\n");
        ImportJob job = job(csv);
        when(importJobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        doThrow(new ConflictException("duplicate credential"))
            .when(rowService).importRow(eq(jobId), eq(1), eq(1), any(Participant.class));
        doAnswer(inv -> {
            job.setProcessedRows(1);
            job.setErrorCount(1);
            return null;
        }).when(rowService).skipRow(jobId, 1, 1);

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.COMPLETED);
        assertThat(job.getProcessedRows()).isEqualTo(1);
        assertThat(job.getSuccessCount()).isZero();
        assertThat(job.getErrorCount()).isEqualTo(1);
    }

    @Test
    void bibAllocationFailureIsRetriedRatherThanSkipped() throws Exception {
        Path csv = tempDir.resolve("bib-failure.csv");
        Files.writeString(csv, "email,firstName,lastName\nnew@example.com,Ada,Runner\n");
        ImportJob job = job(csv);
        when(importJobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        doThrow(new ConflictException("allocation failed", "BIB_ALLOCATION_FAILED"))
            .when(rowService).importRow(eq(jobId), eq(1), eq(1), any(Participant.class));

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.PENDING);
        assertThat(job.getProcessedRows()).isZero();
        assertThat(job.getErrorCount()).isZero();
        verify(rowService, never()).skipRow(eq(jobId), eq(1), eq(1));
        assertThat(Files.exists(csv)).isTrue();
    }

    @Test
    void invalidRowIsPersistedAsSkipped() throws Exception {
        Path csv = tempDir.resolve("invalid.csv");
        Files.writeString(csv, "email,firstName,lastName\ninvalid@example.com,,Runner\n");
        ImportJob job = job(csv);
        when(importJobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        doAnswer(inv -> {
            job.setProcessedRows(1);
            job.setErrorCount(1);
            return null;
        }).when(rowService).skipRow(jobId, 1, 1);

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.COMPLETED);
        assertThat(job.getProcessedRows()).isEqualTo(1);
        assertThat(job.getErrorCount()).isEqualTo(1);
        verify(rowService, never()).importRow(eq(jobId), eq(1), eq(1), any(Participant.class));
    }

    @Test
    void invalidNonblankTrackingBooleanIsSkipped() throws Exception {
        Path csv = tempDir.resolve("invalid-boolean.csv");
        Files.writeString(csv, "email,firstName,lastName,isTrackingEnabled\n"
            + "invalid@example.com,Ada,Runner,maybe\n");
        ImportJob job = job(csv);
        when(importJobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        doAnswer(inv -> {
            job.setProcessedRows(1);
            job.setErrorCount(1);
            return null;
        }).when(rowService).skipRow(jobId, 1, 1);

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.COMPLETED);
        assertThat(job.getErrorCount()).isEqualTo(1);
        assertThat(job.getSuccessCount()).isZero();
        verify(rowService, never()).importRow(eq(jobId), eq(1), eq(1), any(Participant.class));
    }

    @ParameterizedTest
    @CsvSource({
        "email,0,255", "firstName,1,100", "lastName,2,100", "phone,3,20",
        "nationality,4,100", "bibNumber,5,20", "rfidTag,6,100", "registrationSource,7,50"
    })
    void overlongPersistedFieldSkipsRowAndImportsFollowingRow(String field, int column, int limit)
        throws Exception {
        Path csv = tempDir.resolve("overlong-" + field + ".csv");
        String[] invalid = {"invalid@example.com", "Ada", "Runner", "0700", "ZA", "B001", "TAG-1",
            "CSV_IMPORT"};
        invalid[column] = "x".repeat(limit + 1);
        Files.writeString(csv, "email,firstName,lastName,phone,nationality,bibNumber,rfidTag,registrationSource\n"
            + String.join(",", invalid) + "\n"
            + "valid@example.com,Bea,Fast,0711,ZA,B002,TAG-2,CSV_IMPORT\n");
        ImportJob job = job(csv);
        when(importJobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        doAnswer(inv -> {
            job.setProcessedRows(1);
            job.setErrorCount(1);
            return null;
        }).when(rowService).skipRow(jobId, 1, 1);
        doAnswer(inv -> {
            Participant participant = inv.getArgument(3);
            assertThat(participant.getEmail()).isEqualTo("valid@example.com");
            job.setProcessedRows(2);
            job.setSuccessCount(1);
            return null;
        }).when(rowService).importRow(eq(jobId), eq(1), eq(2), any(Participant.class));

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.COMPLETED);
        assertThat(job.getTotalRows()).isEqualTo(2);
        assertThat(job.getProcessedRows()).isEqualTo(2);
        assertThat(job.getSuccessCount()).isEqualTo(1);
        assertThat(job.getErrorCount()).isEqualTo(1);
        verify(rowService, never()).importRow(eq(jobId), eq(1), eq(1), any(Participant.class));
        assertThat(Files.exists(csv)).isFalse();
    }

    @Test
    void previewAndImportAgreeOnOverlongRowThenAcceptSameEmailInValidRow() throws Exception {
        Path csv = tempDir.resolve("preview-length.csv");
        Files.writeString(csv, "email,firstName,lastName,phone\n"
            + "runner@example.com,Ada,Runner,123456789012345678901\n"
            + "runner@example.com,Ada,Runner,0700\n");
        ImportJob job = job(csv);
        when(participantRepository.findEmailsByRaceId(job.getRace().getId())).thenReturn(List.of());
        ImportPreviewDto preview;
        try (InputStream input = Files.newInputStream(csv)) {
            preview = processor.preview(input, "preview-length.csv", job.getRace().getId());
        }

        assertThat(preview.totalRows()).isEqualTo(2);
        assertThat(preview.validRows()).isEqualTo(1);
        assertThat(preview.duplicateRows()).isZero();
        assertThat(preview.rows().get(0).errors()).containsExactly("Field exceeds maximum length: phone");
        assertThat(preview.rows().get(1).errors()).isEmpty();

        when(importJobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        doAnswer(inv -> {
            job.setProcessedRows(1);
            job.setErrorCount(1);
            return null;
        }).when(rowService).skipRow(jobId, 1, 1);
        doAnswer(inv -> {
            Participant participant = inv.getArgument(3);
            assertThat(participant.getEmail()).isEqualTo("runner@example.com");
            job.setProcessedRows(2);
            job.setSuccessCount(1);
            return null;
        }).when(rowService).importRow(eq(jobId), eq(1), eq(2), any(Participant.class));

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.COMPLETED);
        assertThat(job.getProcessedRows()).isEqualTo(2);
        assertThat(job.getSuccessCount()).isEqualTo(preview.validRows());
        assertThat(job.getErrorCount()).isEqualTo(1);
        verify(rowService, never()).importRow(eq(jobId), eq(1), eq(1), any(Participant.class));
    }

    @Test
    void explicitFalseTrackingBooleanIsImported() throws Exception {
        Path csv = tempDir.resolve("false-boolean.csv");
        Files.writeString(csv, "email,firstName,lastName,isTrackingEnabled\n"
            + "valid@example.com,Ada,Runner,false\n");
        ImportJob job = job(csv);
        when(importJobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        doAnswer(inv -> {
            Participant participant = inv.getArgument(3);
            assertThat(participant.getIsTrackingEnabled()).isFalse();
            job.setProcessedRows(1);
            job.setSuccessCount(1);
            return null;
        }).when(rowService).importRow(eq(jobId), eq(1), eq(1), any(Participant.class));

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.COMPLETED);
        assertThat(job.getSuccessCount()).isEqualTo(1);
        assertThat(job.getErrorCount()).isZero();
    }

    @Test
    void infrastructureFailureRetainsSourceAndResumesAfterCommittedRow() throws Exception {
        Path csv = tempDir.resolve("retry.csv");
        Files.writeString(csv, "email,firstName,lastName\na@example.com,Ada,Runner\nb@example.com,Bea,Fast\n");
        ImportJob job = job(csv);
        when(importJobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        doAnswer(inv -> {
            job.setProcessedRows(1);
            job.setSuccessCount(1);
            return null;
        }).when(rowService).importRow(eq(jobId), eq(1), eq(1), any(Participant.class));
        doThrow(new org.springframework.dao.CannotAcquireLockException("database unavailable"))
            .doAnswer(inv -> {
                job.setProcessedRows(2);
                job.setSuccessCount(2);
                return null;
            }).when(rowService).importRow(eq(jobId), anyInt(), eq(2), any(Participant.class));

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.PENDING);
        assertThat(job.getProcessedRows()).isEqualTo(1);
        assertThat(job.getSuccessCount()).isEqualTo(1);
        assertThat(job.getErrorCount()).isZero();
        assertThat(Files.exists(csv)).isTrue();

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.COMPLETED);
        assertThat(job.getProcessedRows()).isEqualTo(2);
        assertThat(job.getSuccessCount()).isEqualTo(2);
        assertThat(job.getErrorCount()).isZero();
        assertThat(job.getAttempts()).isEqualTo(2);
        verify(rowService).importRow(eq(jobId), eq(1), eq(1), any(Participant.class));
        assertThat(Files.exists(csv)).isFalse();
    }

    @Test
    void repeatedInfrastructureFailureMarksFailedAndPreservesSource() throws Exception {
        Path csv = tempDir.resolve("failed.csv");
        Files.writeString(csv, "email,firstName,lastName\na@example.com,Ada,Runner\n");
        ImportJob job = job(csv);
        job.setAttempts(2);
        when(importJobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new org.springframework.dao.CannotAcquireLockException("database unavailable"))
            .when(rowService).importRow(eq(jobId), eq(3), eq(1), any(Participant.class));

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.FAILED);
        assertThat(job.getProcessedRows()).isZero();
        assertThat(job.getErrorCount()).isZero();
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(Files.exists(csv)).isTrue();
    }

    @Test
    void claimFailureLeavesStateUnassertedAndRecoveryContinuesToNextJob(CapturedOutput output) throws Exception {
        Path firstSource = tempDir.resolve("first.csv");
        Path secondSource = tempDir.resolve("second.csv");
        Files.writeString(firstSource, "email,firstName,lastName\n");
        Files.writeString(secondSource, "email,firstName,lastName\n");
        ImportJob first = job(firstSource);
        ImportJob second = job(secondSource);
        UUID secondId = UUID.randomUUID();
        second.setId(secondId);
        ParticipantImportJobService claiming = mock(ParticipantImportJobService.class);
        ParticipantImportProcessor recovering = new ParticipantImportProcessor(importJobRepository,
            participantRepository, raceCategoryRepository, rowService, claiming);
        when(importJobRepository.findByStatusIn(any())).thenReturn(List.of(first, second), List.of(first));
        when(claiming.claim(eq(jobId), any(OffsetDateTime.class)))
            .thenThrow(new org.springframework.dao.CannotAcquireLockException("lock unavailable"))
            .thenReturn(Optional.of(new ParticipantImportJobService.Claim(first, 1)));
        when(claiming.claim(eq(secondId), any(OffsetDateTime.class)))
            .thenReturn(Optional.of(new ParticipantImportJobService.Claim(second, 1)));
        when(claiming.setTotalRows(secondId, 1, 0)).thenReturn(true);
        when(claiming.complete(secondId, 1)).thenReturn(true);
        when(claiming.setTotalRows(jobId, 1, 0)).thenReturn(true);
        when(claiming.complete(jobId, 1)).thenReturn(true);

        recovering.recoverStuckJobs();

        verify(claiming).claim(eq(secondId), any(OffsetDateTime.class));
        verify(claiming, never()).fail(eq(jobId), anyInt(), any(Exception.class));
        assertThat(first.getAttempts()).isZero();
        assertThat(first.getStatus()).isEqualTo(ImportJob.JobStatus.PENDING);
        assertThat(Files.exists(firstSource)).isTrue();
        assertThat(Files.exists(secondSource)).isFalse();
        assertThat(output.getOut()).contains(jobId.toString(), "persisted state is uncertain");

        recovering.recoverStuckJobs();

        verify(claiming, times(2)).claim(eq(jobId), any(OffsetDateTime.class));
        assertThat(Files.exists(firstSource)).isFalse();
    }

    @Test
    void scanFailureIsLoggedForLaterRecovery(CapturedOutput output) {
        when(importJobRepository.findByStatusIn(any()))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("database unavailable"));

        processor.recoverStuckJobs();

        assertThat(output.getOut()).contains("Unable to scan participant import jobs",
            "scheduled recovery will retry");
    }
}
