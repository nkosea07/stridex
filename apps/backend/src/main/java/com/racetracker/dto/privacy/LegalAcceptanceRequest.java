package com.racetracker.dto.privacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LegalAcceptanceRequest {

    @NotBlank
    @Size(max = 40)
    private String privacyPolicyVersion;

    @NotBlank
    @Size(max = 40)
    private String termsVersion;
}
