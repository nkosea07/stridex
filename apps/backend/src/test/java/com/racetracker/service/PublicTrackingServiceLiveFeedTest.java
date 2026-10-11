package com.racetracker.service;

import com.racetracker.exception.ConflictException;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceFeedRevisionRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.impl.PublicTrackingServiceImpl;
import com.racetracker.service.LastCheckpointNameCache;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicTrackingServiceLiveFeedTest {

    @Test
    void liveFeedUsesStableKeysetAndRejectsCursorsAfterMembershipChanges() {
        RaceRepository races = mock(RaceRepository.class);
        ParticipantRepository participants = mock(ParticipantRepository.class);
        GpsLocationRepository gps = mock(GpsLocationRepository.class);
        CheckpointRecordRepository checkpoints = mock(CheckpointRecordRepository.class);
        RaceFeedRevisionRepository revisions = mock(RaceFeedRevisionRepository.class);
        LastCheckpointNameCache checkpointNames = mock(LastCheckpointNameCache.class);
        UUID raceId = UUID.randomUUID();
        Race race = new Race();
        race.setId(raceId);
        race.setStatus(Race.RaceStatus.IN_PROGRESS);
        race.setDistanceKm(BigDecimal.valueOf(10));
        Participant first = liveParticipant(race, "A-10", "Ari", "Runner");
        Participant second = liveParticipant(race, "A-11", "Bea", "Runner");

        when(races.findById(raceId)).thenReturn(Optional.of(race));
        when(revisions.currentRevision(raceId)).thenReturn(4L, 5L);
        when(participants.findPublicLiveAfter(eq(raceId), eq(""), isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(List.of(first, second));
        when(gps.findLatestByRaceIdAndParticipantIds(eq(raceId), any())).thenReturn(List.of());
        when(checkpoints.findByRaceIdAndParticipantIdInOrderByRecordedAtAsc(eq(raceId), any())).thenReturn(List.of());
        when(checkpoints.findTopByParticipantIdAndRaceIdOrderByRecordedAtDesc(first.getId(), raceId)).thenReturn(Optional.empty());

        PublicTrackingServiceImpl service = new PublicTrackingServiceImpl(
            races, participants, gps, checkpoints, revisions, checkpointNames,
            new com.racetracker.config.RaceTrackerProperties());
        var initial = service.getLiveFeed(raceId, null, null, 1);

        assertThat(initial.getItems()).hasSize(1);
        assertThat(initial.getItems().get(0).getBibNumber()).isEqualTo("A-10");
        assertThat(initial.getNextCursor()).isNotBlank();
        verify(participants).findPublicLiveAfter(eq(raceId), eq(""), isNull(), isNull(), isNull(),
            org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 2));

        assertThrows(ConflictException.class, () -> service.getLiveFeed(raceId, null, initial.getNextCursor(), 1));
        verify(participants, times(1)).findPublicLiveAfter(eq(raceId), eq(""), any(), any(), any(), any(Pageable.class));
    }

    private static Participant liveParticipant(Race race, String bib, String first, String last) {
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setBibNumber(bib);
        participant.setFirstName(first);
        participant.setLastName(last);
        participant.setStatus(Participant.ParticipantStatus.STARTED);
        participant.setTotalDistance(BigDecimal.ZERO);
        participant.setRegisteredAt(OffsetDateTime.parse("2026-06-01T08:00:00Z"));
        participant.setCreatedAt(OffsetDateTime.parse("2026-06-01T08:00:00Z"));
        return participant;
    }
}
