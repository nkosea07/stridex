package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotBlank;

public record QrCheckInRequest(@NotBlank String code) {}
