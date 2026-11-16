package com.racetracker.dto.privacy;

import java.time.OffsetDateTime;

public record LegalAcceptanceDto(
    String privacyPolicyVersion,
    OffsetDateTime privacyPolicyAcceptedAt,
    String termsVersion,
    OffsetDateTime termsAcceptedAt
) {
}
