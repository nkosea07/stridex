package com.racetracker.dto.rfid;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RfidReadEventBatchRequest {
    @NotEmpty
    @Size(max = 200)
    private List<@Valid RfidReadEventRequest> reads;
}
