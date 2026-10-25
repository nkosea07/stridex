package com.racetracker.dto.participant;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class PaymentDto {
    private UUID id;
    private UUID participantId;
    private String provider;
    private String status;
    private BigDecimal amount;
    private BigDecimal payableAmount;
    private String currency;
    private String reference;
    private String checkoutUrl;
}
