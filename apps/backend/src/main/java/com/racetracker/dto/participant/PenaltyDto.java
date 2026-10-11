package com.racetracker.dto.participant;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class PenaltyDto {
    private UUID id;
    private UUID participantId;
    private Integer seconds;
    private String reason;
    private OffsetDateTime createdAt;
}
