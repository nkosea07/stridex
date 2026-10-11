package com.racetracker.dto.publictracking;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class PublicRaceAthleteSummaryResponse {
    private UUID participantId;
    private String participantName;
    private String bibNumber;
    private String status;
    private String lastCheckpoint;
    private String elapsedTime;
    private String averagePace;
    private String predictedFinishTime;
    private BigDecimal distanceRemaining;
}
