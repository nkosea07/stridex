package com.racetracker.dto.audit;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditEventDto(
    UUID id,
    UUID actorUserId,
    String action,
    String targetType,
    String targetId,
    String outcome,
    String requestId,
    Map<String, Object> metadata,
    OffsetDateTime createdAt
) { }
