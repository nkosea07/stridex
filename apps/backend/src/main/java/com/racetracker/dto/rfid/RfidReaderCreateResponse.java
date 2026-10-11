package com.racetracker.dto.rfid;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RfidReaderCreateResponse extends RfidReaderDto {
    private String apiKey;
}
