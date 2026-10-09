package com.racetracker.dto.rfid;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RfidReaderDto {
    private UUID id;
    private UUID raceId;
    private UUID checkpointId;
    private String name;
    private String externalId;
    private String status;
    private String lastSeenAt;
    private String lastIp;
    private String gatewayName;
    private String gatewayStatus;
    private Integer gatewayBacklog;
    private String firmwareVersion;
    private String hardwareModel;
    private String hardwareProfile;
    private String readerClass;
    private String lastError;
    private String lastHeartbeatAt;
    private String lastProcessedAt;
    private String createdAt;
    private String updatedAt;
}
