package com.racetracker.service;

import com.racetracker.dto.insights.ParticipantAnomalyDto;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.impl.RaceInsightsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceInsightsServiceImplTest {

    @Mock ParticipantRepository participantRepository;
    @Mock RaceRepository raceRepository;
    @Mock GpsLocationRepository gpsLocationRepository;
    @Mock CheckpointRecordRepository checkpointRecordRepository;

    private RaceInsightsServiceImpl service;
    private final UUID raceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RaceInsightsServiceImpl(participantRepository, raceRepository,
            gpsLocationRepository, checkpointRecordRepository);
    }

    private Race race() {
        Race race = new Race();
        race.setId(raceId);
        race.setDistanceKm(BigDecimal.valueOf(42.2));
        return race;
    }

    @Test
    void anomaliesScoreFilteredGpsAndMissedCutoffs() {
        Race race = race();
        Participant noisy = Participant.builder().id(UUID.randomUUID()).race(race)
            .firstName("Noisy").lastName("Runner").bibNumber("1")
            .status(Participant.ParticipantStatus.STARTED).build();
        Participant disqualified = Participant.builder().id(UUID.randomUUID()).race(race)
            .firstName("Bad").lastName("Actor").bibNumber("2")
            .status(Participant.ParticipantStatus.DISQUALIFIED).build();
        Participant clean = Participant.builder().id(UUID.randomUUID()).race(race)
            .firstName("Clean").lastName("Runner").status(Participant.ParticipantStatus.STARTED).build();

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(participantRepository.findByRaceId(raceId)).thenReturn(List.of(noisy, disqualified, clean));
        when(gpsLocationRepository.countFilteredByRace(raceId))
            .thenReturn(List.<Object[]>of(new Object[] {noisy.getId(), 2L}));
        when(checkpointRecordRepository.countCutoffMissedByRace(raceId))
            .thenReturn(List.<Object[]>of(new Object[] {noisy.getId(), 1L}));

        List<ParticipantAnomalyDto> anomalies = service.anomalies(raceId);

        assertThat(anomalies).hasSize(2);
        assertThat(anomalies.get(0).getParticipantId()).isEqualTo(noisy.getId());
        assertThat(anomalies.get(0).getAnomalyScore()).isEqualTo(3.0);
        assertThat(anomalies.get(1).getParticipantId()).isEqualTo(disqualified.getId());
    }

    @Test
    void summaryProjectsLeaderFinishFromLivePace() {
        Race race = race();
        Participant runner = Participant.builder().id(UUID.randomUUID()).race(race)
            .firstName("Ada").lastName("Runner").status(Participant.ParticipantStatus.STARTED)
            .registeredAt(OffsetDateTime.now().minusHours(1))
            .totalDistance(BigDecimal.valueOf(10))
            .build();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(participantRepository.findByRaceId(raceId)).thenReturn(List.of(runner));
        when(gpsLocationRepository.countFilteredByRace(raceId)).thenReturn(List.of());
        when(checkpointRecordRepository.countCutoffMissedByRace(raceId)).thenReturn(List.of());

        var summary = service.summary(raceId);

        assertThat(summary.getTotalParticipants()).isEqualTo(1);
        assertThat(summary.getLiveParticipants()).isEqualTo(1);
        assertThat(summary.getAveragePaceSecondsPerKm()).isGreaterThan(0);
        assertThat(summary.getPredictedLeaderFinishTime()).isNotNull();
    }
}
