package com.racetracker.dto.media;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MediaConsentDto(UUID participantId, boolean consented, String version, OffsetDateTime acceptedAt) {}
