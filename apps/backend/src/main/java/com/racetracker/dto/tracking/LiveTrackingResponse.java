package com.racetracker.dto.tracking;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class LiveTrackingResponse {
    private UUID participantId;
    private String bibNumber;
    private String participantName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal speed;
    private String lastCheckpoint;
    private String elapsedTime;
    private String status;
    private String predictedFinishTime;
    private BigDecimal distanceRemaining;
    private String averagePace;
    private java.util.List<CheckpointSplitDto> splits;
}
