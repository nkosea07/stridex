package com.racetracker.dto.checkpoint;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CheckpointDto {
    private UUID id;
    private UUID raceId;
    private String name;
    private String checkpointType;
    private BigDecimal distanceFromStartKm;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer orderIndex;
    private String cutoffTime;
    private Boolean hasRfidReader;
    private Boolean hasQrScanner;
    private Long recordCount;
}
