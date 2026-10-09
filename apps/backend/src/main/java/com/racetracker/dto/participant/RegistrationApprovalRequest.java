package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationApprovalRequest {

    @NotBlank
    private String decision;

    private String reason;

    private Boolean force;
}
