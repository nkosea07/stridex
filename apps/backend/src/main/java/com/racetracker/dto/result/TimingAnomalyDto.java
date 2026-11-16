package com.racetracker.dto.result;

import java.math.BigDecimal;
import java.util.UUID;

public record TimingAnomalyDto(
    UUID participantId,
    String participantName,
    String bibNumber,
    String type,
    UUID checkpointId,
    String checkpointName,
    BigDecimal speedKmh
) {
}
