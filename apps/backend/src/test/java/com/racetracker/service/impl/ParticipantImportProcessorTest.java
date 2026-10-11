package com.racetracker.service.impl;

import com.racetracker.model.ImportJob;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.ImportJobRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceCategoryRepository;
import com.racetracker.service.ParticipantCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantImportProcessorTest {

    @Mock ImportJobRepository importJobRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock ParticipantCredentialService participantCredentialService;
    @Mock RaceCategoryRepository raceCategoryRepository;

    @TempDir Path tempDir;

    private ParticipantImportProcessor processor;
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        processor = new ParticipantImportProcessor(importJobRepository, participantRepository,
            participantCredentialService, raceCategoryRepository);
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
        return job;
    }

    @Test
    void runJobImportsRowsAndMarksCompleted() throws Exception {
        Path csv = tempDir.resolve("participants.csv");
        Files.writeString(csv, "email,firstName,lastName,phone\n"
            + "a@example.com,Ada,Runner,0700\n"
            + "b@example.com,Bea,Fast,0711\n", StandardCharsets.UTF_8);

        ImportJob job = job(csv);
        when(importJobRepository.findWithRaceById(jobId)).thenReturn(Optional.of(job));
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(participantRepository.findEmailsByRaceId(job.getRace().getId())).thenReturn(List.of());
        when(participantRepository.save(any(Participant.class))).thenAnswer(inv -> inv.getArgument(0));

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.COMPLETED);
        assertThat(job.getSuccessCount()).isEqualTo(2);
        assertThat(job.getAttempts()).isEqualTo(1);
    }

    @Test
    void runJobSkipsDuplicateEmails() throws Exception {
        Path csv = tempDir.resolve("dupes.csv");
        Files.writeString(csv, "email,firstName,lastName\n"
            + "existing@example.com,Ada,Runner\n"
            + "new@example.com,Bea,Fast\n", StandardCharsets.UTF_8);

        ImportJob job = job(csv);
        when(importJobRepository.findWithRaceById(jobId)).thenReturn(Optional.of(job));
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(participantRepository.findEmailsByRaceId(job.getRace().getId()))
            .thenReturn(List.of("existing@example.com"));
        lenient().when(participantRepository.save(any(Participant.class))).thenAnswer(inv -> inv.getArgument(0));

        processor.runJob(jobId);

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.COMPLETED);
        assertThat(job.getSuccessCount()).isEqualTo(1);
        assertThat(job.getErrorCount()).isEqualTo(1);
    }
}
