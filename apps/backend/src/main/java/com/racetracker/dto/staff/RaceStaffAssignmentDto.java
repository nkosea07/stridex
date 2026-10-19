package com.racetracker.dto.staff;

import com.racetracker.model.User;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RaceStaffAssignmentDto(
    UUID id,
    UUID raceId,
    UUID userId,
    User.UserRole role,
    boolean active,
    OffsetDateTime assignedAt,
    OffsetDateTime revokedAt
) {
}
