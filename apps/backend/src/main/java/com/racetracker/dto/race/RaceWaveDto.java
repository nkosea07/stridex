package com.racetracker.dto.race;

import java.util.UUID;

public record RaceWaveDto(UUID id, String name, int sortOrder, String scheduledStart, String startedAt, long participants) {
}
