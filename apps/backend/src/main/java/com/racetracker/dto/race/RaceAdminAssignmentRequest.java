package com.racetracker.dto.race;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RaceAdminAssignmentRequest {

    @NotNull
    private UUID userId;
}
