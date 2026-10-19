package com.racetracker.service;

import com.racetracker.dto.result.RaceResultPageDto;
import com.racetracker.exception.ConflictException;
import com.racetracker.mapper.ParticipantMapper;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.RaceResult;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantPenaltyRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceCategoryRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceResultRepository;
import com.racetracker.repository.RaceResultRevisionRepository;
import com.racetracker.repository.RaceResultRowProjection;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.ResultServiceImpl;
import com.racetracker.security.CapabilityAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultPersistenceTest {

    @Mock private RaceRepository raceRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private CheckpointRecordRepository checkpointRecordRepository;
    @Mock private UserRepository userRepository;
    @Mock private ParticipantMapper participantMapper;
    @Mock private RaceResultRevisionRepository resultRevisionRepository;
    @Mock private ParticipantPenaltyRepository penaltyRepository;
    @Mock private RaceResultRepository raceResultRepository;
    @Mock private RaceCategoryRepository raceCategoryRepository;
    @Mock private CapabilityAuthorizationService capabilityAuthorizationService;

    private ResultServiceImpl resultService;
    private final UUID raceId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        resultService = new ResultServiceImpl(raceRepository, participantRepository, checkpointRecordRepository,
            userRepository, participantMapper, resultRevisionRepository, penaltyRepository,
            raceResultRepository, raceCategoryRepository, capabilityAuthorizationService);
    }

    private Race completedRace() {
        Race race = new Race();
        race.setId(raceId);
        race.setStatus(Race.RaceStatus.COMPLETED);
        return race;
    }

    @Test
    void publishRejectsRaceThatIsNotCompleted() {
        Race race = completedRace();
        race.setStatus(Race.RaceStatus.IN_PROGRESS);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> resultService.publish(raceId))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("must be completed");

        verify(raceResultRepository, never()).saveAll(any());
    }

    @Test
    void publishSnapshotsComputedResults() {
        Race race = completedRace();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

        Participant participant = Participant.builder()
            .id(participantId)
            .race(race)
            .status(Participant.ParticipantStatus.FINISHED)
            .bibNumber("A-1")
            .registeredAt(OffsetDateTime.parse("2026-01-01T07:00:00Z"))
            .build();

        RaceResultRowProjection projection = mock(RaceResultRowProjection.class);
        when(projection.getParticipantId()).thenReturn(participantId);
        when(projection.getOverallPosition()).thenReturn(1L);

        when(participantRepository.findResultRows(eq(raceId), isNull(), anyBoolean(), isNull(), any(Pageable.class)))
            .thenReturn(List.of(projection));
        when(participantRepository.countResultRows(eq(raceId), isNull(), anyBoolean())).thenReturn(1L);
        when(participantRepository.findByIdIn(List.of(participantId))).thenReturn(List.of(participant));

        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T08:00:00Z");
        CheckpointRecord startRecord = CheckpointRecord.builder()
            .participant(participant)
            .checkpoint(Checkpoint.builder().checkpointType(Checkpoint.CheckpointType.START).build())
            .recordedAt(start).build();
        CheckpointRecord finishRecord = CheckpointRecord.builder()
            .participant(participant)
            .checkpoint(Checkpoint.builder().checkpointType(Checkpoint.CheckpointType.FINISH).build())
            .recordedAt(start.plusHours(1)).build();
        when(checkpointRecordRepository.findByParticipantIdInOrderByRecordedAtAsc(List.of(participantId)))
            .thenReturn(List.of(startRecord, finishRecord));
        when(penaltyRepository.findByParticipantIdIn(List.of(participantId))).thenReturn(List.of());
        when(participantRepository.getReferenceById(participantId)).thenReturn(participant);

        resultService.publish(raceId);

        verify(raceResultRepository).deleteByRaceId(raceId);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RaceResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(raceResultRepository).saveAll(captor.capture());

        List<RaceResult> snapshots = captor.getValue();
        assertThat(snapshots).hasSize(1);
        RaceResult snapshot = snapshots.get(0);
        assertThat(snapshot.getParticipant()).isSameAs(participant);
        assertThat(snapshot.getOverallPosition()).isEqualTo(1);
        assertThat(snapshot.getGunTime()).isEqualTo(Duration.ofHours(1));
        assertThat(snapshot.getIsOfficial()).isTrue();
        assertThat(race.getResultsPublishedAt()).isNotNull();
    }

    @Test
    void servedResultsComeFromPersistedRowsOncePublished() {
        Race race = completedRace();
        race.setResultsPublishedAt(OffsetDateTime.now());
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(resultRevisionRepository.currentRevision(raceId)).thenReturn(1L);

        Participant participant = Participant.builder()
            .id(participantId).race(race)
            .status(Participant.ParticipantStatus.FINISHED).build();

        RaceResult persisted = RaceResult.builder()
            .participant(participant)
            .race(race)
            .chipTime(Duration.ofMinutes(90))
            .gunTime(Duration.ofMinutes(91))
            .overallPosition(5)
            .isOfficial(true)
            .isDisqualified(false)
            .penaltySeconds(0)
            .build();

        when(raceResultRepository.countByRaceId(raceId)).thenReturn(1L);
        when(raceResultRepository.findPageByRace(eq(raceId), isNull(), any(Pageable.class)))
            .thenReturn(List.of(persisted));
        when(raceResultRepository.countPageByRace(eq(raceId), isNull())).thenReturn(1L);
        when(checkpointRecordRepository.findByParticipantIdInOrderByRecordedAtAsc(List.of(participantId)))
            .thenReturn(List.of());

        RaceResultPageDto page = resultService.listByRacePage(raceId, 0, 50, null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).getOverallPosition()).isEqualTo(5);
        assertThat(page.items().get(0).getTotalTime()).isEqualTo("01:30:00");
        assertThat(page.items().get(0).isOfficial()).isTrue();
        verify(participantRepository, never()).findResultRows(any(), any(), anyBoolean(), any(), any());
    }
}
