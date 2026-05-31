package com.racetracker.dto.checkpoint;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CheckpointUpdateRequest {
    private String name;
    private String checkpointType;
    private BigDecimal distanceFromStartKm;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer orderIndex;
    private String cutoffTime;
    private Boolean hasRfidReader;
    private Boolean hasQrScanner;
}
