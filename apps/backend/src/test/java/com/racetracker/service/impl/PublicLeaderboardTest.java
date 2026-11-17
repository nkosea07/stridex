package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.publictracking.PublicRaceAthleteSummaryResponse;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceFeedRevisionRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.LastCheckpointNameCache;
import com.racetracker.service.PublicRaceVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicLeaderboardTest {

    private final RaceRepository races = mock(RaceRepository.class);
    private final ParticipantRepository participants = mock(ParticipantRepository.class);
    private final GpsLocationRepository gps = mock(GpsLocationRepository.class);
    private final CheckpointRecordRepository records = mock(CheckpointRecordRepository.class);
    private final PublicTrackingServiceImpl service = new PublicTrackingServiceImpl(new PublicRaceVisibility(races),
        participants, gps, records, mock(RaceFeedRevisionRepository.class), mock(LastCheckpointNameCache.class),
        new RaceTrackerProperties());
    private final UUID raceId = UUID.randomUUID();
    private final Race race = new Race();

    private Participant runner(String name, double coveredKm) {
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setFirstName(name);
        participant.setLastName("Runner");
        participant.setStatus(Participant.ParticipantStatus.STARTED);
        participant.setTotalDistance(BigDecimal.valueOf(coveredKm));
        return participant;
    }

    private void onCourse(List<Participant> firstPage, List<Participant> secondPage) {
        race.setId(raceId);
        race.setStatus(Race.RaceStatus.IN_PROGRESS);
        race.setDistanceKm(BigDecimal.valueOf(42.195));
        when(races.findByIdForPublicVisibility(raceId)).thenReturn(Optional.of(race));
        when(participants.findPublicOnCourseByRaceId(eq(raceId), any(Pageable.class))).thenAnswer(invocation -> {
            Pageable page = invocation.getArgument(1);
            List<Participant> content = page.getPageNumber() == 0 ? firstPage : secondPage;
            return new PageImpl<>(content, page, page.getPageSize() + secondPage.size());
        });
        when(gps.findLatestByRaceIdAndParticipantIds(any(), any())).thenReturn(List.of());
        when(records.findByRaceIdAndParticipantIdInOrderByRecordedAtAsc(any(), any())).thenReturn(List.of());
    }

    @Test
    void ranksEveryoneOnCourseNotJustTheFirstPage() {
        Participant chaser = runner("Chaser", 10);
        Participant leader = runner("Leader", 30);
        onCourse(List.of(chaser), List.of(leader));

        List<PublicRaceAthleteSummaryResponse> board = service.getLeaderboard(raceId, 50);

        assertThat(board).extracting(PublicRaceAthleteSummaryResponse::getParticipantId)
            .containsExactly(leader.getId(), chaser.getId());
        assertThat(board).extracting(PublicRaceAthleteSummaryResponse::getRank).containsExactly(1, 2);
        verify(participants).findPublicOnCourseByRaceId(raceId, PageRequest.of(1, 500));
    }

    @Test
    void repeatedRequestsWithinFifteenSecondsReuseTheRanking() {
        onCourse(List.of(runner("A", 5)), List.of(runner("B", 6)));

        service.getLeaderboard(raceId, 10);
        service.getLeaderboard(raceId, 10);
        service.getRaceHub(raceId);

        verify(participants, times(2)).findPublicOnCourseByRaceId(eq(raceId), any(Pageable.class));
    }

    @Test
    void limitIsBoundedAndTheHubShowsTheTopTen() {
        List<Participant> field = java.util.stream.IntStream.range(0, 12)
            .mapToObj(index -> runner("R" + index, index)).toList();
        onCourse(field, List.of());

        assertThat(service.getLeaderboard(raceId, 3)).hasSize(3).first()
            .extracting(PublicRaceAthleteSummaryResponse::getParticipantName).isEqualTo("R11 Runner");
        assertThat(service.getLeaderboard(raceId, 10_000)).hasSize(12);
        assertThat(service.getRaceHub(raceId).getLeaderboard()).hasSize(10);
    }

    @Test
    void ordersByDistanceRemainingThenElapsedAndPutsUnknownProgressLast() {
        PublicRaceAthleteSummaryResponse unknown = row("Unknown", null, null);
        PublicRaceAthleteSummaryResponse slower = row("Slower", 5.0, "02:10:00");
        PublicRaceAthleteSummaryResponse faster = row("Faster", 5.0, "02:05:00");
        PublicRaceAthleteSummaryResponse ahead = row("Ahead", 2.0, "02:30:00");

        assertThat(PublicTrackingServiceImpl.rank(List.of(unknown, slower, faster, ahead)))
            .extracting(PublicRaceAthleteSummaryResponse::getParticipantName)
            .containsExactly("Ahead", "Faster", "Slower", "Unknown");
    }

    private PublicRaceAthleteSummaryResponse row(String name, Double remainingKm, String elapsed) {
        PublicRaceAthleteSummaryResponse row = new PublicRaceAthleteSummaryResponse();
        row.setParticipantId(UUID.randomUUID());
        row.setParticipantName(name);
        row.setDistanceRemaining(remainingKm == null ? null : BigDecimal.valueOf(remainingKm));
        row.setElapsedTime(elapsed);
        return row;
    }
}
