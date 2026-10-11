package com.racetracker.dto.participant;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RegistrationAnswerDto {
    private UUID fieldId;
    private String fieldKey;
    private String label;
    private String value;
}
