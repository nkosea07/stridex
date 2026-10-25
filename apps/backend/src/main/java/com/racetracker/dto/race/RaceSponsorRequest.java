package com.racetracker.dto.race;

import jakarta.validation.constraints.NotBlank;

public record RaceSponsorRequest(@NotBlank String name, String logoUrl, String websiteUrl,
                                 String tier, Integer displayOrder) {}
