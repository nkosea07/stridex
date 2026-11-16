package com.racetracker.service;

import com.racetracker.dto.dashboard.DashboardStatsDto;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceImplTest {

    private final RaceRepository races = mock(RaceRepository.class);
    private final ParticipantRepository participants = mock(ParticipantRepository.class);
    private final GpsLocationRepository gps = mock(GpsLocationRepository.class);
    private final CheckpointRecordRepository checkpoints = mock(CheckpointRecordRepository.class);
    private final DashboardServiceImpl service = new DashboardServiceImpl(races, participants, gps, checkpoints);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            email, null, List.of(new SimpleGrantedAuthority(role))));
    }

    @Test
    void raceAdminSeesOnlyTheirOwnRaces() {
        signIn("organizer@example.com", "ROLE_RACE_ADMIN");
        when(races.countManagedBy("organizer@example.com")).thenReturn(2L);
        when(participants.countInRacesManagedBy("organizer@example.com")).thenReturn(150L);

        DashboardStatsDto stats = service.getStats();

        assertThat(stats.getTotalRaces()).isEqualTo(2L);
        assertThat(stats.getTotalParticipants()).isEqualTo(150L);
        verify(races, never()).count();
        verify(participants, never()).count();
        verify(gps, never()).countDistinctParticipantsActiveSince(any());
        verify(checkpoints).countFinishRecordsSinceInRacesManagedBy(eq("organizer@example.com"), any());
    }

    @Test
    void superAdminSeesPlatformTotals() {
        signIn("root@example.com", "ROLE_SUPER_ADMIN");
        when(races.count()).thenReturn(40L);
        when(races.countByStatusIn(anyCollection())).thenReturn(5L);

        DashboardStatsDto stats = service.getStats();

        assertThat(stats.getTotalRaces()).isEqualTo(40L);
        verify(races, never()).countManagedBy(any());
    }
}
