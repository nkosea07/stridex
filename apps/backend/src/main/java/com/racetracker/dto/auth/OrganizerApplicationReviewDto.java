package com.racetracker.dto.auth;

import com.racetracker.model.OrganizerApplication;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizerApplicationReviewDto(
    UUID id,
    String applicantEmail,
    String applicantFirstName,
    String applicantLastName,
    boolean emailVerified,
    String organizationName,
    OrganizerApplication.ApplicationStatus status,
    String reviewNotes,
    OffsetDateTime createdAt,
    OffsetDateTime reviewedAt,
    String reviewedByEmail
) {
}
