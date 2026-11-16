package com.racetracker.dto.race;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class WaveAssignmentRequest {
    @NotEmpty
    @Size(max = 5000)
    private List<UUID> participantIds;
}
