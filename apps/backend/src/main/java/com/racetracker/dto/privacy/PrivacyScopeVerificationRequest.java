package com.racetracker.dto.privacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrivacyScopeVerificationRequest(
    @NotBlank @Size(max = 1024) String scope,
    @NotBlank @Size(max = 2000) String reason
) {}
