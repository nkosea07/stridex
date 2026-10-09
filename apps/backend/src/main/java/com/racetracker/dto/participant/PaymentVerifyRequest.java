package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentVerifyRequest {

    @NotBlank
    private String status;

    private String reference;

    private BigDecimal amount;
}
