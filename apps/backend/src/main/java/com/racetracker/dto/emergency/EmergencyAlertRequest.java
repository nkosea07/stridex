package com.racetracker.dto.emergency;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class EmergencyAlertRequest {

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Size(max = 2000)
    private String message;
}
