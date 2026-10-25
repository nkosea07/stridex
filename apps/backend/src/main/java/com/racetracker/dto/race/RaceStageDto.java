package com.racetracker.dto.race;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record RaceStageDto(UUID id, UUID raceId, String name, Integer sequence, LocalDate stageDate,
                           LocalTime startTime, BigDecimal distanceKm, String routeDescription) {}
