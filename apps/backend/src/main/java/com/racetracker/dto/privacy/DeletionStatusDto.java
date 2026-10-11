package com.racetracker.dto.privacy;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class DeletionStatusDto {
    private boolean deletionRequested;
    private OffsetDateTime requestedAt;
    private OffsetDateTime scheduledFor;
}
