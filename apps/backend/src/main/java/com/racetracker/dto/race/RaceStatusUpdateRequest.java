package com.racetracker.dto.race;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RaceStatusUpdateRequest {
    @NotBlank
    private String status;
}
