package com.racetracker.dto.race;

import java.util.UUID;

public record RaceSponsorDto(UUID id, UUID raceId, String name, String logoUrl, String websiteUrl,
                             String tier, Integer displayOrder) {}
