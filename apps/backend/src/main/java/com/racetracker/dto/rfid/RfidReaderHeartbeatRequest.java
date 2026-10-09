package com.racetracker.dto.rfid;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class RfidReaderHeartbeatRequest {
    private String gatewayName;
    private String gatewayStatus;

    @Min(0)
    private Integer gatewayBacklog;

    private String firmwareVersion;
    private String hardwareModel;
    private String hardwareProfile;
    private String readerClass;
    private String lastError;
    private OffsetDateTime observedAt;
}
