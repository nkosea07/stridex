package com.racetracker.dto.checkpoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckpointRecordVoidRequest {
    @NotBlank
    @Size(min = 5, max = 500)
    private String reason;
}
