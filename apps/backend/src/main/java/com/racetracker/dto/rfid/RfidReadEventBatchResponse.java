package com.racetracker.dto.rfid;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RfidReadEventBatchResponse {
    private int accepted;
    private int duplicate;
    private int unknownTag;
    private int invalidReader;
    private int error;
    private List<RfidReadEventResultDto> results = new ArrayList<>();
}
