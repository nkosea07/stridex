package com.racetracker.dto.tracking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GpsLocationBatchRequest {
    @NotEmpty
    private List<@Valid GpsLocationRequest> locations;
}
