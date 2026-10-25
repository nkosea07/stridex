package com.racetracker.dto.result;

import java.math.BigDecimal;
import java.util.UUID;

public record PersonalBestDto(
    BigDecimal distanceKm,
    String chipTime,
    UUID raceId,
    String raceName,
    int resultCount
) {}
