package com.racetracker.dto.participant;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReversalCaseDto(
    UUID paymentId,
    UUID raceId,
    String raceName,
    UUID participantId,
    String participantName,
    String provider,
    String providerReference,
    BigDecimal amount,
    String currency,
    String paymentStatus,
    OffsetDateTime observedAt,
    UUID pendingRefundAttemptId
) {}
