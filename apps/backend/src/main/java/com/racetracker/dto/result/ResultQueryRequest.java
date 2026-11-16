package com.racetracker.dto.result;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ResultQueryRequest {
    private UUID checkpointId;
    @NotBlank
    @Size(min = 10, max = 1000)
    private String message;
}
