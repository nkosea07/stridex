package com.racetracker.dto.race;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class RaceWaveRequest {
    @NotBlank
    @Size(max = 100)
    private String name;
    private Integer sortOrder;
    private OffsetDateTime scheduledStart;
}
