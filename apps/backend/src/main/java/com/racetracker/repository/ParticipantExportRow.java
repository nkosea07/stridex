package com.racetracker.repository;

import com.racetracker.model.Participant;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ParticipantExportRow(
    UUID id,
    String firstName,
    String lastName,
    String email,
    String phone,
    String bibNumber,
    Participant.ParticipantStatus status,
    Participant.Gender gender,
    String nationality,
    OffsetDateTime registeredAt
) {
}
