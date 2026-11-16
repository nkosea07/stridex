package com.racetracker.dto.tracking;

import java.math.BigDecimal;
import java.util.UUID;

public record SelfScanClaimDto(
    UUID id,
    UUID participantId,
    String participantName,
    String bibNumber,
    UUID checkpointId,
    String checkpointName,
    String claimedAt,
    String reason,
    BigDecimal distanceMeters,
    Integer fixAgeSeconds,
    String status
) {
}
