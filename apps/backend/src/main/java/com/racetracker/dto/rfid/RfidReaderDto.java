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
    private String createdAt;
    private String updatedAt;
}
