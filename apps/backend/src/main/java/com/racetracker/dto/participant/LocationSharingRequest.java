package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotNull;

public record LocationSharingRequest(@NotNull Boolean enabled) {}
