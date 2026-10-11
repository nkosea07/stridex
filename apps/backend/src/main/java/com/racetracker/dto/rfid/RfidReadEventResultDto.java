package com.racetracker.dto.rfid;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RfidReadEventResultDto {
    private String sourceEventId;
    private String rfidTag;
    private String status;
    private UUID participantId;
    private UUID checkpointRecordId;
    private String errorMessage;
}
