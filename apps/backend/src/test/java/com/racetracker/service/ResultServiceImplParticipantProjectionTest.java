package com.racetracker.service;

import com.racetracker.dto.result.ParticipantResultDto;
import com.racetracker.mapper.ParticipantMapper;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantPenaltyRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceCategoryRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceResultRepository;
import com.racetracker.repository.RaceResultRevisionRepository;
import com.racetracker.repository.RaceResultRowProjection;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.CapabilityAuthorizationService;
import com.racetracker.service.impl.ResultServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultServiceImplParticipantProjectionTest {

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

    private final UUID raceId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();
    private ResultServiceImpl resultService;
    private Race race;
    private Participant participant;
    private RaceResultRowProjection projection;

    @BeforeEach
    void setUp() {
        resultService = new ResultServiceImpl(raceRepository, participantRepository, checkpointRecordRepository,
            userRepository, participantMapper, resultRevisionRepository, penaltyRepository,
            raceResultRepository, raceCategoryRepository, capabilityAuthorizationService);

        race = new Race();
        race.setId(raceId);
        participant = Participant.builder()
            .id(participantId)
            .race(race)
            .status(Participant.ParticipantStatus.FINISHED)
            .bibNumber("A-1")
            .build();
        projection = org.mockito.Mockito.mock(RaceResultRowProjection.class);
        when(projection.getParticipantId()).thenReturn(participantId);
        when(projection.getOverallPosition()).thenReturn(1L);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(checkpointRecordRepository.findByParticipantIdInOrderByRecordedAtAsc(List.of(participantId)))
            .thenReturn(List.of());
        when(penaltyRepository.findByParticipantIdIn(List.of(participantId))).thenReturn(List.of());
    }

    @Test
    void resultListAndPageUseTheSafeParticipantProjection() {
        ParticipantResultDto safeParticipant = safeParticipant();
        when(participantMapper.toResultDto(participant)).thenReturn(safeParticipant);
        when(resultRevisionRepository.currentRevision(raceId)).thenReturn(1L);
        when(participantRepository.findByIdIn(List.of(participantId))).thenReturn(List.of(participant));
        when(participantRepository.findResultRows(eq(raceId), isNull(), eq(false), isNull(), any(Pageable.class)))
            .thenReturn(List.of(projection));
        when(participantRepository.countResultRows(eq(raceId), isNull(), anyBoolean())).thenReturn(1L);

        assertThat(resultService.listByRace(raceId).get(0).getParticipant()).isSameAs(safeParticipant);
        assertThat(resultService.listByRacePage(raceId, 0, 50, null).items().get(0).getParticipant())
            .isSameAs(safeParticipant);

        verify(participantMapper, never()).toDto(participant);
        verify(participantMapper, org.mockito.Mockito.atLeastOnce()).toResultDto(participant);
    }

    @Test
    void resultDetailUsesTheSafeParticipantProjection() {
        ParticipantResultDto safeParticipant = safeParticipant();
        when(participantMapper.toResultDto(participant)).thenReturn(safeParticipant);
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(participantRepository.findByIdIn(List.of(participantId))).thenReturn(List.of(participant));
        when(participantRepository.findResultRows(eq(raceId), isNull(), eq(false), eq(participantId), any(Pageable.class)))
            .thenReturn(List.of(projection));

        assertThat(resultService.getByRaceAndParticipant(raceId, participantId).getParticipant())
            .isSameAs(safeParticipant);

        verify(participantMapper, never()).toDto(participant);
        verify(participantMapper).toResultDto(participant);
    }

    private ParticipantResultDto safeParticipant() {
        ParticipantResultDto dto = new ParticipantResultDto();
        dto.setId(participantId);
        dto.setRaceId(raceId);
        dto.setFirstName("Taylor");
        dto.setLastName("Runner");
        dto.setGender("FEMALE");
        dto.setNationality("ZW");
        dto.setBibNumber("A-1");
        dto.setStatus("FINISHED");
        dto.setClubName("City Club");
        return dto;
    }
}
