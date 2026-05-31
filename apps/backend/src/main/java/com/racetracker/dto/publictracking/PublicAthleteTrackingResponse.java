package com.racetracker.dto.publictracking;

import com.racetracker.dto.tracking.CheckpointSplitDto;
import com.racetracker.dto.tracking.ParticipantTrackPointDto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class PublicAthleteTrackingResponse {
    private UUID participantId;
    private String participantName;
    private String bibNumber;
    private String status;
    private String lastCheckpoint;
    private String elapsedTime;
    private String averagePace;
    private String predictedFinishTime;
    private BigDecimal distanceRemaining;
    private List<CheckpointSplitDto> splits = new ArrayList<>();
    private List<ParticipantTrackPointDto> locations = new ArrayList<>();
}
