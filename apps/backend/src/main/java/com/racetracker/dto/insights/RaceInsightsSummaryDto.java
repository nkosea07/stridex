package com.racetracker.dto.insights;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RaceInsightsSummaryDto {
    private long totalParticipants;
    private long liveParticipants;
    private double averagePaceSecondsPerKm;
    private String predictedLeaderFinishTime;
    private long anomalyCount;
}
