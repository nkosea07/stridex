package com.racetracker.dto.tracking;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TrackingCheckpointRequest {
    private UUID checkpointId;
    private String method;
    private String timestamp;
    @jakarta.validation.constraints.Size(max = 128)
    private String scanToken;
    @jakarta.validation.Valid
    private GpsLocationRequest location;
}
