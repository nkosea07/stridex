package com.racetracker.service;

import com.racetracker.dto.participant.ParticipantDto;
import com.racetracker.dto.result.RaceResultDto;
import com.racetracker.dto.result.RankingEntryDto;
import com.racetracker.service.impl.RankingServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceImplTest {

    @Mock ResultService resultService;

    private RaceResultDto result(String team, String club, String country, String time) {
        ParticipantDto participant = new ParticipantDto();
        participant.setTeamName(team);
        participant.setClubName(club);
        participant.setNationality(country);

        RaceResultDto dto = new RaceResultDto();
        dto.setId(UUID.randomUUID());
        dto.setParticipant(participant);
        dto.setTotalTime(time);
        dto.setOfficial(true);
        dto.setDisqualified(false);
        return dto;
    }

    @Test
    void aggregatesTeamTotalsAndSortsByTotalTime() {
        UUID raceId = UUID.randomUUID();
        when(resultService.listByRace(raceId)).thenReturn(List.of(
            result("Alpha", null, "ZIM", "01:00:00"),
            result("Alpha", null, "ZIM", "01:10:00"),
            result("Bravo", null, "ZIM", "00:50:00")
        ));

        RankingServiceImpl service = new RankingServiceImpl(resultService);
        List<RankingEntryDto> rankings = service.teamRankings(raceId);

        assertThat(rankings).hasSize(2);
        assertThat(rankings.get(0).getName()).isEqualTo("Bravo");
        assertThat(rankings.get(0).getTotalTime()).isEqualTo("00:50:00");
        assertThat(rankings.get(1).getName()).isEqualTo("Alpha");
        assertThat(rankings.get(1).getFinishers()).isEqualTo(2);
        assertThat(rankings.get(1).getTotalTime()).isEqualTo("02:10:00");
        assertThat(rankings.get(1).getBestTime()).isEqualTo("01:00:00");
    }

    @Test
    void aggregatesByCountryUpperCase() {
        UUID raceId = UUID.randomUUID();
        when(resultService.listByRace(raceId)).thenReturn(List.of(
            result(null, null, "zim", "01:00:00"),
            result(null, null, "ZAF", "00:45:00")
        ));

        RankingServiceImpl service = new RankingServiceImpl(resultService);
        List<RankingEntryDto> rankings = service.countryRankings(raceId);

        assertThat(rankings).extracting(RankingEntryDto::getName).containsExactly("ZAF", "ZIM");
    }
}
