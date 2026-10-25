package com.racetracker.dto.privacy;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PrivacyDeletionJobDto(
    UUID id,
    UUID userId,
    String storageKey,
    String storageScope,
    String status,
    int attempts,
    OffsetDateTime nextAttemptAt,
    OffsetDateTime createdAt
) {}
