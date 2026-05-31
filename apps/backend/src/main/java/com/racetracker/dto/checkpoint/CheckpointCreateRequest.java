package com.racetracker.dto.checkpoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CheckpointCreateRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String checkpointType;
    private BigDecimal distanceFromStartKm;
    private BigDecimal latitude;
    private BigDecimal longitude;
    @NotNull
    private Integer orderIndex;
    private String cutoffTime;
    private Boolean hasRfidReader;
    private Boolean hasQrScanner;
}
