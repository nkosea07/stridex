package com.racetracker.dto.tracking;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class GpsLocationRequest {
    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private BigDecimal latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private BigDecimal longitude;

    private BigDecimal altitude;
    private BigDecimal accuracy;
    private BigDecimal speed;
    private BigDecimal heading;
    private Integer batteryLevel;

    @NotNull
    private String timestamp;
}
