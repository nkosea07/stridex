package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParticipantCredentialRequest {
    @NotBlank
    private String type;

    @NotBlank
    private String value;
}
