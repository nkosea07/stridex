package com.racetracker.dto.tracking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GpsLocationBatchRequest {
    @NotEmpty
    @Size(max = 500)
    private List<@Valid GpsLocationRequest> locations;
}
