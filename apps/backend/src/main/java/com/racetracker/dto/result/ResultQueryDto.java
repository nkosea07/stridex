package com.racetracker.dto.result;

import java.util.UUID;

public record ResultQueryDto(
    UUID id,
    UUID raceId,
    UUID participantId,
    String participantName,
    String bibNumber,
    UUID checkpointId,
    String checkpointName,
    String message,
    String status,
    String resolutionNote,
    String createdAt,
    String resolvedAt
) {
}
