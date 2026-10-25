package com.racetracker.dto.race;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record RaceStageRequest(@NotBlank String name, @NotNull @Positive Integer sequence,
                               LocalDate stageDate, LocalTime startTime, BigDecimal distanceKm,
                               String routeDescription) {}
