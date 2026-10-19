package com.racetracker.dto.staff;

import com.racetracker.model.StaffRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StaffPromotionRequest {

    @NotNull
    private StaffRole role;
}
