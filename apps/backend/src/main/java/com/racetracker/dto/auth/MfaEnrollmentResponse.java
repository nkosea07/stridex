package com.racetracker.dto.auth;

public record MfaEnrollmentResponse(String secret, String provisioningUri) {
}
