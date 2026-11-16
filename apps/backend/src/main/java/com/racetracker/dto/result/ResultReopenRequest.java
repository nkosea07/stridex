package com.racetracker.dto.result;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResultReopenRequest {

    @NotBlank
    @Size(min = 5, max = 500)
    private String reason;
}
