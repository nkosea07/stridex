package com.racetracker.dto.checkpoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CheckpointRecordReassignRequest {
    private UUID participantId;

    @Size(max = 50)
    private String participantBibNumber;

    @NotBlank
    @Size(min = 5, max = 500)
    private String reason;
}
