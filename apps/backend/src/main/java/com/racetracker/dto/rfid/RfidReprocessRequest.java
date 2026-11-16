package com.racetracker.dto.rfid;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RfidReprocessRequest {

    public enum Scope { UNKNOWN_TAG, IGNORED, ERROR }

    @NotEmpty
    @Size(max = 3)
    private List<Scope> statuses;
}
