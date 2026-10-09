package com.racetracker.dto.checkpoint;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class CheckpointRecordCorrectionRequest {
    private OffsetDateTime adjustedRecordedAt;
    private String reason;
    private Boolean verified;
}
