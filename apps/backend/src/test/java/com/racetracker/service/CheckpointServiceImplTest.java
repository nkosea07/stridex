package com.racetracker.service;

import com.racetracker.dto.checkpoint.CheckpointRecordDto;
import com.racetracker.dto.checkpoint.CheckpointRecordRequest;
import com.racetracker.mapper.CheckpointMapper;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.impl.CheckpointServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckpointServiceImplTest {

    @Mock CheckpointRepository checkpointRepository;
    @Mock CheckpointRecordRepository checkpointRecordRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock RaceRepository raceRepository;
    @Mock UserRepository userRepository;
    @Mock CheckpointMapper checkpointMapper;
    @Mock LiveSummaryCacheInvalidator liveSummaryCacheInvalidator;
    @Mock RaceAccessGuard raceAccessGuard;

    private CheckpointServiceImpl service;
    private final UUID raceId = UUID.randomUUID();
    private final UUID checkpointId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CheckpointServiceImpl(checkpointRepository, checkpointRecordRepository, participantRepository,
            raceRepository, userRepository, checkpointMapper, liveSummaryCacheInvalidator, raceAccessGuard);
    }

    @Test
    void recordFlagsCutoffMissAndTransitionsParticipantToDnf() {
        Race race = new Race();
        race.setId(raceId);
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setId(checkpointId);
        checkpoint.setRace(race);
        checkpoint.setCheckpointType(Checkpoint.CheckpointType.INTERMEDIATE);
        checkpoint.setCutoffTime(Duration.ofHours(1));

        Participant participant = Participant.builder().id(participantId).race(race)
            .status(Participant.ParticipantStatus.STARTED)
            .checkedInAt(OffsetDateTime.now().minusHours(3))
            .build();

        when(checkpointRepository.findByRaceIdAndId(raceId, checkpointId)).thenReturn(Optional.of(checkpoint));
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(checkpointRecordRepository.findByParticipantIdAndCheckpointId(participantId, checkpointId))
            .thenReturn(Optional.empty());
        when(checkpointRecordRepository.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participantId, raceId))
            .thenReturn(List.of());
        when(checkpointRecordRepository.save(any(CheckpointRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(checkpointMapper.toRecordDto(any(CheckpointRecord.class))).thenReturn(new CheckpointRecordDto());

        CheckpointRecordRequest request = new CheckpointRecordRequest();
        request.setParticipantId(participantId);
        request.setMethod("MANUAL");

        service.record(raceId, checkpointId, request);

        ArgumentCaptor<CheckpointRecord> captor = ArgumentCaptor.forClass(CheckpointRecord.class);
        org.mockito.Mockito.verify(checkpointRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getIsCutoffMissed()).isTrue();
        assertThat(participant.getStatus()).isEqualTo(Participant.ParticipantStatus.DNF);
    }

    @Test
    void recordWithinCutoffKeepsCutoffFlagFalse() {
        Race race = new Race();
        race.setId(raceId);
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setId(checkpointId);
        checkpoint.setRace(race);
        checkpoint.setCheckpointType(Checkpoint.CheckpointType.INTERMEDIATE);
        checkpoint.setCutoffTime(Duration.ofHours(5));

        Participant participant = Participant.builder().id(participantId).race(race)
            .status(Participant.ParticipantStatus.STARTED)
            .checkedInAt(OffsetDateTime.now().minusMinutes(30))
            .build();

        when(checkpointRepository.findByRaceIdAndId(raceId, checkpointId)).thenReturn(Optional.of(checkpoint));
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(checkpointRecordRepository.findByParticipantIdAndCheckpointId(participantId, checkpointId))
            .thenReturn(Optional.empty());
        when(checkpointRecordRepository.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participantId, raceId))
            .thenReturn(List.of());
        when(checkpointRecordRepository.save(any(CheckpointRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(checkpointMapper.toRecordDto(any(CheckpointRecord.class))).thenReturn(new CheckpointRecordDto());

        CheckpointRecordRequest request = new CheckpointRecordRequest();
        request.setParticipantId(participantId);
        request.setMethod("MANUAL");

        service.record(raceId, checkpointId, request);

        assertThat(participant.getStatus()).isEqualTo(Participant.ParticipantStatus.STARTED);
    }

    @Test
    void recordEnforcesCheckpointScopedRaceAccessBeforeLoadingCheckpoint() {
        doThrow(new org.springframework.security.access.AccessDeniedException("wrong checkpoint"))
            .when(raceAccessGuard).assertCanAccessCheckpoint(raceId, checkpointId);

        CheckpointRecordRequest request = new CheckpointRecordRequest();
        request.setParticipantId(participantId);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.record(raceId, checkpointId, request))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verifyNoInteractions(checkpointRepository, participantRepository, checkpointRecordRepository);
    }

    @Test
    void liveRecordRejectsClientSuppliedTimestamp() {
        CheckpointRecordRequest request = new CheckpointRecordRequest();
        request.setParticipantId(participantId);
        request.setRecordedAt(OffsetDateTime.now().minusHours(2));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.record(raceId, checkpointId, request))
            .isInstanceOf(com.racetracker.exception.ApiException.class)
            .extracting("code").isEqualTo("CHECKPOINT_TIMESTAMP_NOT_ALLOWED");

        verifyNoInteractions(checkpointRepository, participantRepository, checkpointRecordRepository);
    }

    @Test
    void importedRecordUsesHistoricalTimestampAfterRaceAuthorization() {
        Race race = new Race();
        race.setId(raceId);
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setId(checkpointId);
        checkpoint.setRace(race);
        Participant participant = Participant.builder().id(participantId).race(race).build();
        OffsetDateTime historicalTime = OffsetDateTime.now().minusHours(2);
        CheckpointRecordRequest request = new CheckpointRecordRequest();
        request.setParticipantId(participantId);
        request.setRecordedAt(historicalTime);
        when(checkpointRepository.findByRaceIdAndId(raceId, checkpointId)).thenReturn(Optional.of(checkpoint));
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(checkpointRecordRepository.findByParticipantIdAndCheckpointId(participantId, checkpointId))
            .thenReturn(Optional.empty());
        when(checkpointRecordRepository.save(any(CheckpointRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(checkpointMapper.toRecordDto(any(CheckpointRecord.class))).thenReturn(new CheckpointRecordDto());

        service.recordImported(raceId, checkpointId, request);

        org.mockito.Mockito.verify(raceAccessGuard).assertCanImportTiming(raceId);
        ArgumentCaptor<CheckpointRecord> captor = ArgumentCaptor.forClass(CheckpointRecord.class);
        org.mockito.Mockito.verify(checkpointRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getRecordedAt()).isEqualTo(historicalTime);
    }
}
