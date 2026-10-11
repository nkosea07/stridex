package com.racetracker.dto.participant;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class ParticipantCredentialDto {
    private UUID id;
    private String type;
    private String value;
    private String status;
    private OffsetDateTime assignedAt;
    private OffsetDateTime revokedAt;
}
