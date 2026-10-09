package com.racetracker.dto.participant;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TeamDto {
    private UUID id;
    private UUID raceId;
    private String name;
    private String clubName;
    private String status;
    private UUID captainUserId;
    private long memberCount;
}
