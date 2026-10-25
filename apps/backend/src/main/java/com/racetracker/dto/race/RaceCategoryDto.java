package com.racetracker.dto.race;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class RaceCategoryDto {
    private UUID id;
    private UUID raceId;
    private String name;
    private String description;
    private Integer minAge;
    private Integer maxAge;
    private String gender;
    private String bibPrefix;
    private Integer maxParticipants;
    private BigDecimal registrationFee;
    private BigDecimal earlyBirdFee;
    private BigDecimal teamFee;
    private Integer participantCount;
}
