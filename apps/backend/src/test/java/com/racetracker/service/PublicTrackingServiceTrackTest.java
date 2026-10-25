package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.GpsLocation;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.HeatCellProjection;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceFeedRevisionRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.impl.PublicTrackingServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicTrackingServiceTrackTest {

    private PublicTrackingServiceImpl newService(RaceRepository races, ParticipantRepository participants,
                                                 GpsLocationRepository gps, CheckpointRecordRepository checkpoints,
                                                 RaceFeedRevisionRepository revisions) {
        return new PublicTrackingServiceImpl(
            new PublicRaceVisibility(races), participants, gps, checkpoints, revisions, mock(LastCheckpointNameCache.class),
            new RaceTrackerProperties());
    }

    @Test
    void athleteTrackExcludesFilteredPointsAndUsesSmoothedCoordinates() {
        RaceRepository races = mock(RaceRepository.class);
        ParticipantRepository participants = mock(ParticipantRepository.class);
        GpsLocationRepository gps = mock(GpsLocationRepository.class);
        CheckpointRecordRepository checkpoints = mock(CheckpointRecordRepository.class);
        RaceFeedRevisionRepository revisions = mock(RaceFeedRevisionRepository.class);

        UUID raceId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        Race race = new Race();
        race.setId(raceId);
        race.setStatus(Race.RaceStatus.IN_PROGRESS);
        Participant participant = new Participant();
        participant.setId(participantId);
        participant.setRace(race);
        participant.setIsTrackingEnabled(true);

        GpsLocation filtered = new GpsLocation();
        filtered.setLatitude(BigDecimal.valueOf(-33.9));
        filtered.setLongitude(BigDecimal.valueOf(18.4));
        filtered.setFiltered(true);
        filtered.setRecordedAt(OffsetDateTime.now());

        GpsLocation accepted = new GpsLocation();
        accepted.setLatitude(BigDecimal.valueOf(-33.8));
        accepted.setLongitude(BigDecimal.valueOf(18.5));
        accepted.setSmoothedLatitude(BigDecimal.valueOf(-33.81));
        accepted.setSmoothedLongitude(BigDecimal.valueOf(18.51));
        accepted.setFiltered(false);
        accepted.setRecordedAt(OffsetDateTime.now().plusSeconds(10));

        when(races.findByIdForPublicVisibility(raceId)).thenReturn(Optional.of(race));
        when(participants.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(gps.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participantId, raceId)).thenReturn(List.of(filtered, accepted));

        var response = newService(races, participants, gps, checkpoints, revisions).getAthleteTrack(raceId, participantId);

        assertThat(response.getPoints()).hasSize(1);
        assertThat(response.getPoints().get(0).getLat()).isEqualByComparingTo(BigDecimal.valueOf(-33.81));
        assertThat(response.getPoints().get(0).getLng()).isEqualByComparingTo(BigDecimal.valueOf(18.51));
    }

    @Test
    void athleteTrackEmptyWhenTrackingDisabled() {
        RaceRepository races = mock(RaceRepository.class);
        ParticipantRepository participants = mock(ParticipantRepository.class);
        GpsLocationRepository gps = mock(GpsLocationRepository.class);
        CheckpointRecordRepository checkpoints = mock(CheckpointRecordRepository.class);
        RaceFeedRevisionRepository revisions = mock(RaceFeedRevisionRepository.class);

        UUID raceId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        Race race = new Race();
        race.setId(raceId);
        race.setStatus(Race.RaceStatus.IN_PROGRESS);
        Participant participant = new Participant();
        participant.setId(participantId);
        participant.setRace(race);
        participant.setIsTrackingEnabled(false);

        when(races.findByIdForPublicVisibility(raceId)).thenReturn(Optional.of(race));
        when(participants.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));

        var response = newService(races, participants, gps, checkpoints, revisions).getAthleteTrack(raceId, participantId);

        assertThat(response.getPoints()).isEmpty();
    }

    @Test
    void athleteTrackingSplitsCarryNumericPaceData() {
        RaceRepository races = mock(RaceRepository.class);
        ParticipantRepository participants = mock(ParticipantRepository.class);
        GpsLocationRepository gps = mock(GpsLocationRepository.class);
        CheckpointRecordRepository checkpoints = mock(CheckpointRecordRepository.class);
        RaceFeedRevisionRepository revisions = mock(RaceFeedRevisionRepository.class);

        UUID raceId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        Race race = new Race();
        race.setId(raceId);
        race.setStatus(Race.RaceStatus.IN_PROGRESS);

        OffsetDateTime start = OffsetDateTime.parse("2026-06-02T06:00:00Z");
        Participant participant = new Participant();
        participant.setId(participantId);
        participant.setRace(race);
        participant.setCheckedInAt(start);

        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setName("10K");
        checkpoint.setDistanceFromStartKm(BigDecimal.valueOf(10));
        CheckpointRecord record = new CheckpointRecord();
        record.setParticipant(participant);
        record.setCheckpoint(checkpoint);
        record.setRecordedAt(start.plusMinutes(48));

        when(races.findByIdForPublicVisibility(raceId)).thenReturn(Optional.of(race));
        when(participants.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(gps.findLatestByRaceIdAndParticipantIds(raceId, List.of(participantId))).thenReturn(List.of());
        when(checkpoints.findByRaceIdAndParticipantIdInOrderByRecordedAtAsc(raceId, List.of(participantId)))
            .thenReturn(List.of(record));
        when(checkpoints.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participantId, raceId))
            .thenReturn(List.of(record));

        var response = newService(races, participants, gps, checkpoints, revisions)
            .getAthleteTracking(raceId, participantId);

        assertThat(response.getSplits()).hasSize(1);
        assertThat(response.getSplits().get(0).getDistanceMeters()).isEqualTo(10000);
        assertThat(response.getSplits().get(0).getDurationSeconds()).isEqualTo(2880L);
        assertThat(response.getSplits().get(0).getPaceSecondsPerKm()).isEqualTo(288L);
    }

    @Test
    void heatmapMapsProjectionCells() {
        RaceRepository races = mock(RaceRepository.class);
        ParticipantRepository participants = mock(ParticipantRepository.class);
        GpsLocationRepository gps = mock(GpsLocationRepository.class);
        CheckpointRecordRepository checkpoints = mock(CheckpointRecordRepository.class);
        RaceFeedRevisionRepository revisions = mock(RaceFeedRevisionRepository.class);

        UUID raceId = UUID.randomUUID();
        Race race = new Race();
        race.setId(raceId);
        race.setStatus(Race.RaceStatus.IN_PROGRESS);

        HeatCellProjection cell = mock(HeatCellProjection.class);
        when(cell.getLatitude()).thenReturn(-33.9);
        when(cell.getLongitude()).thenReturn(18.4);
        when(cell.getWeight()).thenReturn(12L);

        when(races.findByIdForPublicVisibility(raceId)).thenReturn(Optional.of(race));
        when(gps.heatCells(eq(raceId), any(), any(), eq(3))).thenReturn(List.of(cell));

        var cells = newService(races, participants, gps, checkpoints, revisions).getRaceHeatmap(raceId, null, null, null);

        assertThat(cells).hasSize(1);
        assertThat(cells.get(0).getLatitude()).isEqualTo(-33.9);
        assertThat(cells.get(0).getWeight()).isEqualTo(12L);
    }
}
