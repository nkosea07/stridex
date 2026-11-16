package com.racetracker.dto.privacy;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class DeletionStatusDto {
    private boolean deletionRequested;
    private OffsetDateTime requestedAt;
    private OffsetDateTime scheduledFor;
    private List<String> blockers = List.of();
}
