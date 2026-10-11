package com.racetracker.dto.participant;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class RaceRegistrationFieldDto {
    private UUID id;
    private UUID raceId;
    private String fieldKey;
    private String label;
    private String fieldType;
    private Boolean required;
    private List<String> options;
    private Integer displayOrder;
    private Boolean active;
    private OffsetDateTime createdAt;
}
