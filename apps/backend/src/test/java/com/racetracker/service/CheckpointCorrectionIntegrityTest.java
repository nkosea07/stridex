package com.racetracker.service;

import com.racetracker.dto.checkpoint.CheckpointRecordCorrectionRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.mapper.CheckpointMapper;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.CheckpointRecordCorrection;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordCorrectionRepository;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.impl.CheckpointServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckpointCorrectionIntegrityTest {

    private final CheckpointRepository checkpointRepository = mock(CheckpointRepository.class);
    private final CheckpointRecordRepository recordRepository = mock(CheckpointRecordRepository.class);
    private final RaceRepository raceRepository = mock(RaceRepository.class);
    private final CheckpointRecordCorrectionRepository corrections = mock(CheckpointRecordCorrectionRepository.class);
    private final RfidReadEventRepository readEvents = mock(RfidReadEventRepository.class);
    private final AuditEventService audit = mock(AuditEventService.class);
    private CheckpointServiceImpl service;
    private Race race;
    private Checkpoint checkpoint;
    private CheckpointRecord record;

    @BeforeEach
    void setUp() {
        ParticipantRepository participantRepository = mock(ParticipantRepository.class);
        service = new CheckpointServiceImpl(checkpointRepository, recordRepository, participantRepository, raceRepository,
            mock(UserRepository.class), mock(CheckpointMapper.class), mock(LiveSummaryCacheInvalidator.class),
            mock(RaceAccessGuard.class), mock(CheckpointProgression.class),
            new CutoffReevaluation(recordRepository, participantRepository), new ResultLock(raceRepository), audit,
            corrections, readEvents);
        race = Race.builder().id(UUID.randomUUID()).status(Race.RaceStatus.COMPLETED).build();
        checkpoint = Checkpoint.builder().id(UUID.randomUUID()).race(race).name("Finish")
            .checkpointType(Checkpoint.CheckpointType.FINISH).selfScanToken("t").build();
        Participant participant = Participant.builder().id(UUID.randomUUID()).race(race).build();
        record = new CheckpointRecord();
        record.setId(UUID.randomUUID());
        record.setRace(race);
        record.setCheckpoint(checkpoint);
        record.setParticipant(participant);
        record.setRecordedAt(OffsetDateTime.parse("2026-11-15T09:30:00Z"));
        record.setIsVerified(true);
        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(checkpointRepository.findByRaceIdAndId(race.getId(), checkpoint.getId())).thenReturn(Optional.of(checkpoint));
        when(recordRepository.findByRaceIdAndCheckpointIdAndId(race.getId(), checkpoint.getId(), record.getId()))
            .thenReturn(Optional.of(record));
        when(recordRepository.save(any(CheckpointRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recordRepository.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(any(), any())).thenReturn(List.of(record));
    }

    private CheckpointRecordCorrectionRequest correction(String reason, OffsetDateTime adjusted) {
        CheckpointRecordCorrectionRequest request = new CheckpointRecordCorrectionRequest();
        request.setReason(reason);
        request.setAdjustedRecordedAt(adjusted);
        return request;
    }

    @Test
    void everyCorrectionNeedsAReason() {
        assertThatThrownBy(() -> service.correctRecord(race.getId(), checkpoint.getId(), record.getId(),
            correction("", OffsetDateTime.parse("2026-11-15T09:29:50Z"))))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CORRECTION_REASON_REQUIRED");
        verify(corrections, never()).save(any());
    }

    @Test
    void correctionsKeepTheFullHistoryAndAreAudited() {
        service.correctRecord(race.getId(), checkpoint.getId(), record.getId(),
            correction("Video review: chip read late", OffsetDateTime.parse("2026-11-15T09:29:50Z")));
        service.correctRecord(race.getId(), checkpoint.getId(), record.getId(),
            correction("Second review: photo finish", OffsetDateTime.parse("2026-11-15T09:29:52Z")));

        ArgumentCaptor<CheckpointRecordCorrection> history = ArgumentCaptor.forClass(CheckpointRecordCorrection.class);
        verify(corrections, org.mockito.Mockito.times(2)).save(history.capture());
        assertThat(history.getAllValues().get(0).getPreviousEffectiveAt()).isEqualTo(OffsetDateTime.parse("2026-11-15T09:30:00Z"));
        assertThat(history.getAllValues().get(1).getPreviousEffectiveAt()).isEqualTo(OffsetDateTime.parse("2026-11-15T09:29:50Z"));
        assertThat(record.getRecordedAt()).isEqualTo(OffsetDateTime.parse("2026-11-15T09:30:00Z"));
        verify(audit, org.mockito.Mockito.times(2)).recordInCurrentTransaction(eq("CHECKPOINT_TIME_CORRECTED"), any(),
            eq("CHECKPOINT_RECORD"), eq(record.getId().toString()), eq("SUCCESS"), any());
    }

    @Test
    void certifiedResultsBlockCorrections() {
        race.setResultsCertifiedAt(OffsetDateTime.now());

        assertThatThrownBy(() -> service.correctRecord(race.getId(), checkpoint.getId(), record.getId(),
            correction("Late protest", OffsetDateTime.parse("2026-11-15T09:29:50Z"))))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo(ResultLock.RESULTS_CERTIFIED);
    }

    @Test
    void checkpointsWithTimingEvidenceCannotBeDeleted() {
        when(recordRepository.countByCheckpointId(checkpoint.getId())).thenReturn(0L);
        when(readEvents.existsByCheckpointId(checkpoint.getId())).thenReturn(true);
        race.setStatus(Race.RaceStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.delete(race.getId(), checkpoint.getId()))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CHECKPOINT_HAS_TIMING_DATA");
        verify(checkpointRepository, never()).delete(any());
    }

    @Test
    void offlineCapturesMustBeRecentAndNotInTheFuture() {
        race.setStatus(Race.RaceStatus.IN_PROGRESS);
        com.racetracker.dto.checkpoint.CheckpointRecordRequest future = new com.racetracker.dto.checkpoint.CheckpointRecordRequest();
        future.setParticipantBibNumber("101");
        future.setCapturedOfflineAt(OffsetDateTime.now().plusHours(1));
        assertThatThrownBy(() -> service.record(race.getId(), checkpoint.getId(), future))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("OFFLINE_CAPTURE_OUT_OF_RANGE");

        com.racetracker.dto.checkpoint.CheckpointRecordRequest stale = new com.racetracker.dto.checkpoint.CheckpointRecordRequest();
        stale.setParticipantBibNumber("101");
        stale.setCapturedOfflineAt(OffsetDateTime.now().minusDays(2));
        assertThatThrownBy(() -> service.record(race.getId(), checkpoint.getId(), stale))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("OFFLINE_CAPTURE_OUT_OF_RANGE");
        verify(recordRepository, never()).save(any());
    }
}
