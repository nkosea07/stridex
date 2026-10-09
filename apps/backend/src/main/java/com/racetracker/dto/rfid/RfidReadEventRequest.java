package com.racetracker.dto.rfid;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class RfidReadEventRequest {
    private String sourceEventId;

    @NotBlank
    private String rfidTag;

    @NotNull
    private OffsetDateTime readAt;

    private String antenna;
    private BigDecimal rssi;
    private String rawPayload;

    private String eventOrigin;
}
