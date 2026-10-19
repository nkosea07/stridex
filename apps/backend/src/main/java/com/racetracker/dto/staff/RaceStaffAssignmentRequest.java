package com.racetracker.dto.staff;

import com.racetracker.model.StaffRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RaceStaffAssignmentRequest {

    @NotNull
    private UUID userId;

    @NotNull
    private StaffRole role;
}
