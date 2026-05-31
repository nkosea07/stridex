package com.racetracker.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardStatsDto {
    private long totalRaces;
    private long activeRaces;
    private long totalParticipants;
    private long liveTracking;
    private long upcomingRaces;
    private long completedToday;
}
