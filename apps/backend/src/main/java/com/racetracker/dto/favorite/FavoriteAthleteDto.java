package com.racetracker.dto.favorite;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class FavoriteAthleteDto {
    private UUID raceId;
    private String raceName;
    private UUID participantId;
    private String participantName;
    private String bibNumber;
    private String status;
    private String lastCheckpoint;
    private BigDecimal distanceRemaining;
}
