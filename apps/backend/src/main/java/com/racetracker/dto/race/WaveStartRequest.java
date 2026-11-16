package com.racetracker.dto.race;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class WaveStartRequest {
    private OffsetDateTime startedAt;
    @Size(max = 500)
    private String reason;
}
