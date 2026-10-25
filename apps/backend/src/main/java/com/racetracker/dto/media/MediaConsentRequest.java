package com.racetracker.dto.media;

import jakarta.validation.constraints.NotNull;

public record MediaConsentRequest(@NotNull Boolean consented) {}
