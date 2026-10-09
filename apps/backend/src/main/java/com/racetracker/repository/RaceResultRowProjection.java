package com.racetracker.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RaceResultRowProjection {
    UUID getParticipantId();
    UUID getRaceId();
    UUID getCategoryId();
    String getBibNumber();
    String getStatus();
    String getGender();
    OffsetDateTime getStartTime();
    OffsetDateTime getFinishTime();
    Long getOverallPosition();
    Long getGenderPosition();
    Long getCategoryPosition();
}
