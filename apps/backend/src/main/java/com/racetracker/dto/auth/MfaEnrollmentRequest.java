package com.racetracker.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaEnrollmentRequest {
    @NotBlank
    private String challengeToken;
}
