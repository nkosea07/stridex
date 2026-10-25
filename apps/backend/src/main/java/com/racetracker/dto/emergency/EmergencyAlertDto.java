package com.racetracker.dto.emergency;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class EmergencyAlertDto {
    private UUID id;
    private UUID raceId;
    private UUID participantId;
    private String participantName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String message;
    private String status;
    private String deliveryStatus;
    private OffsetDateTime createdAt;
}
