package com.racetracker.service;

import com.racetracker.dto.auth.*;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(String refreshToken);
    AuthResponse issueMfaSession(java.util.UUID userId);
    void revokeRefreshToken(String refreshToken);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    void verifyEmail(String token);
    void resendVerification(ResendVerificationRequest request);
    UserDto getCurrentUser();
    UserDto updateProfile(UpdateProfileRequest request);
}
