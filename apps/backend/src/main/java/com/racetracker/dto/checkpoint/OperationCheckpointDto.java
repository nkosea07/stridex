package com.racetracker.dto.checkpoint;

import java.util.UUID;

public record OperationCheckpointDto(UUID raceId, String raceName, UUID checkpointId, String checkpointName) {
}
