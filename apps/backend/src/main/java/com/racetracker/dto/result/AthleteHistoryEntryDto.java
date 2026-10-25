package com.racetracker.dto.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AthleteHistoryEntryDto(
    UUID raceId,
    String raceName,
    LocalDate raceDate,
    BigDecimal distanceKm,
    String chipTime,
    Integer overallPosition,
    Integer categoryPosition,
    boolean official,
    boolean disqualified,
    String medal,
    String qualificationStatus
) {}
