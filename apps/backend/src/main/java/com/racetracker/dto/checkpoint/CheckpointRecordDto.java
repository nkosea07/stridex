package com.racetracker.dto.checkpoint;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CheckpointRecordDto {
    private UUID id;
    private UUID participantId;
    private UUID checkpointId;
    private UUID raceId;
    private String recordedAt;
    private String recordMethod;
    private UUID recordedById;
    private String splitTime;
    private String elapsedTime;
    private Boolean isVerified;
    private String notes;
}
