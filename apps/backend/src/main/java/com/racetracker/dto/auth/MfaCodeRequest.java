package com.racetracker.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaCodeRequest {

    private String challengeToken;

    @NotBlank
    @Size(max = 64)
    private String code;
}
