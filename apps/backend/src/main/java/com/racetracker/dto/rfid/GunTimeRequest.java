package com.racetracker.dto.rfid;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class GunTimeRequest {

    @NotNull
    private OffsetDateTime startedAt;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
