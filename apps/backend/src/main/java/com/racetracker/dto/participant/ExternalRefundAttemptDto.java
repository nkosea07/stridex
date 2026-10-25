package com.racetracker.dto.participant;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExternalRefundAttemptDto(
    UUID id,
    UUID paymentId,
    BigDecimal amount,
    String currency,
    String status,
    String paymentReference,
    String providerReference,
    OffsetDateTime createdAt
) {}
