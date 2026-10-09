package com.racetracker.dto.auth;

import java.util.List;

public record OrganizerApplicationPageDto(
    List<OrganizerApplicationReviewDto> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
