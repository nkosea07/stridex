package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RegistrationAnswerRequest {

    @NotNull
    private UUID fieldId;

    private String value;
}
