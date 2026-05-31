package com.racetracker.dto.result;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ResultSplitDto {
    private UUID id;
    private UUID checkpointId;
    private String timestamp;
    private String method;
    private String splitTime;
}
