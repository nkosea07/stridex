package com.racetracker.dto.participant;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StaffParticipantSearchResultDto(
    UUID id,
    String bibNumber,
    String firstName,
    String lastName,
    String categoryName,
    String waveName,
    String status,
    OffsetDateTime checkedInAt
) {
}
