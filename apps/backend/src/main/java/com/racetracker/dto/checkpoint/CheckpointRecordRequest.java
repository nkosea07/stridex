package com.racetracker.dto.checkpoint;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CheckpointRecordRequest {
    private UUID participantId;
    private String participantBibNumber;
    private String method;
}
