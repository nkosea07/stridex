package com.racetracker.dto.emergency;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class EmergencyAlertRequest {

    @NotNull
    private BigDecimal latitude;

    @NotNull
    private BigDecimal longitude;

    private String message;
}
