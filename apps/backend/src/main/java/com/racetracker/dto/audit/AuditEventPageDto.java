package com.racetracker.dto.audit;

import java.util.List;

public record AuditEventPageDto(
    List<AuditEventDto> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) { }
