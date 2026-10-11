package com.racetracker.dto.insights;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ParticipantAnomalyDto {
    private UUID participantId;
    private String participantName;
    private String bibNumber;
    private String status;
    private long filteredGpsPoints;
    private long missedCutoffs;
    private double anomalyScore;
}
