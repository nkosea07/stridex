package com.racetracker.dto.rfid;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RfidReaderBreakdownDto {
    private UUID readerId;
    private String name;
    private UUID checkpointId;
    private String status;
    private String gatewayStatus;
    private Integer backlog;
    private Long lastHeartbeatAgeSeconds;
    private String firmwareVersion;
    private String hardwareModel;
    private String hardwareProfile;
    private String readerClass;
    private String lastError;
}
