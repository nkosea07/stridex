package com.racetracker.dto.rfid;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RfidEventTrendPointDto {
    private String bucket;
    private long total;
    private long accepted;
    private long duplicate;
    private long unknownCredential;
    private long error;
}
