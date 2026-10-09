package com.racetracker.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String refreshToken;
    private Boolean requiresEmailVerification;
    private UserDto user;
    private Boolean mfaRequired;
    private Boolean mfaEnrollmentRequired;
    private String mfaChallengeToken;
    private List<String> recoveryCodes;
}
