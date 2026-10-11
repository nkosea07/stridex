package com.racetracker.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OAuthLoginRequest {

    @NotBlank
    private String idToken;

    private String provider = "google";
}
