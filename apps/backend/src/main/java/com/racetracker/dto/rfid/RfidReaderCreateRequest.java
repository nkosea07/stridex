package com.racetracker.dto.rfid;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RfidReaderCreateRequest {
    @NotNull
    private UUID checkpointId;

    @NotBlank
    private String name;

    @NotBlank
    private String externalId;
}
