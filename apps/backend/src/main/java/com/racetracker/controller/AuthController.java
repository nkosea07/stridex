package com.racetracker.controller;

import com.racetracker.dto.auth.*;
import com.racetracker.service.AuthService;
import com.racetracker.service.OrganizerApplicationService;
import com.racetracker.service.impl.MfaAuthenticationServiceImpl;
import com.racetracker.security.ClientAddressResolver;
import com.racetracker.security.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v${racetracker.api.version}/auth")
public class AuthController {

    private final AuthService authService;
    private final OrganizerApplicationService organizerApplicationService;
    private final MfaAuthenticationServiceImpl mfaAuthenticationService;
    private final RequestRateLimiter requestRateLimiter;
    private final ClientAddressResolver clientAddressResolver;

    public AuthController(
        AuthService authService,
        OrganizerApplicationService organizerApplicationService,
        MfaAuthenticationServiceImpl mfaAuthenticationService,
        RequestRateLimiter requestRateLimiter,
        ClientAddressResolver clientAddressResolver
    ) {
        this.authService = authService;
        this.organizerApplicationService = organizerApplicationService;
        this.mfaAuthenticationService = mfaAuthenticationService;
        this.requestRateLimiter = requestRateLimiter;
        this.clientAddressResolver = clientAddressResolver;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        requestRateLimiter.checkRegistration(clientAddressResolver.resolve(servletRequest));
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/register-organizer")
    public ResponseEntity<AuthResponse> registerOrganizer(@Valid @RequestBody RegisterOrganizerRequest request, HttpServletRequest servletRequest) {
        requestRateLimiter.checkRegistration(clientAddressResolver.resolve(servletRequest));
        return ResponseEntity.ok(organizerApplicationService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        requestRateLimiter.checkLogin(request.getEmail(), clientAddressResolver.resolve(servletRequest));
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }

    @PostMapping("/mfa/totp/enroll")
    public ResponseEntity<MfaEnrollmentResponse> beginTotpEnrollment(@Valid @RequestBody MfaEnrollmentRequest request, HttpServletRequest servletRequest) {
        rateLimitMfa(request.getChallengeToken(), servletRequest);
        return ResponseEntity.ok(mfaAuthenticationService.beginEnrollment(request.getChallengeToken()));
    }

    @PostMapping("/mfa/totp/confirm")
    public ResponseEntity<AuthResponse> confirmTotpEnrollment(@Valid @RequestBody MfaCodeRequest request, HttpServletRequest servletRequest) {
        rateLimitMfa(request.getChallengeToken(), servletRequest);
        var confirmation = mfaAuthenticationService.confirmEnrollment(request.getChallengeToken(), request.getCode());
        AuthResponse response = authService.issueMfaSession(confirmation.userId());
        response.setRecoveryCodes(confirmation.recoveryCodes());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/verify-login")
    public ResponseEntity<AuthResponse> verifyMfaLogin(@Valid @RequestBody MfaCodeRequest request, HttpServletRequest servletRequest) {
        rateLimitMfa(request.getChallengeToken(), servletRequest);
        return ResponseEntity.ok(authService.issueMfaSession(
            mfaAuthenticationService.completeLogin(request.getChallengeToken(), request.getCode())
        ));
    }

    @PostMapping("/mfa/recovery-codes/regenerate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MfaRecoveryCodesResponse> regenerateRecoveryCodes(
        @Valid @RequestBody MfaCodeRequest request,
        Authentication authentication,
        HttpServletRequest servletRequest
    ) {
        requestRateLimiter.checkMfa(authentication.getName(), clientAddressResolver.resolve(servletRequest));
        return ResponseEntity.ok(new MfaRecoveryCodesResponse(
            mfaAuthenticationService.regenerateRecoveryCodes(authentication.getName(), request.getCode())
        ));
    }

    @PostMapping("/mfa/totp/disable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiMessageResponse> disableTotp(
        @Valid @RequestBody MfaCodeRequest request,
        Authentication authentication,
        HttpServletRequest servletRequest
    ) {
        requestRateLimiter.checkMfa(authentication.getName(), clientAddressResolver.resolve(servletRequest));
        mfaAuthenticationService.disable(authentication.getName(), request.getCode());
        return ResponseEntity.ok(new ApiMessageResponse("MFA disabled"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiMessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest servletRequest) {
        requestRateLimiter.checkPasswordReset(request.getEmail(), clientAddressResolver.resolve(servletRequest));
        authService.forgotPassword(request);
        return ResponseEntity.ok(new ApiMessageResponse("If the email exists, password reset instructions have been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiMessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest servletRequest) {
        requestRateLimiter.checkPasswordReset(null, clientAddressResolver.resolve(servletRequest));
        authService.resetPassword(request);
        return ResponseEntity.ok(new ApiMessageResponse("Password reset successful"));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiMessageResponse> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new ApiMessageResponse("Email verified successfully"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiMessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok(new ApiMessageResponse("If the email exists, a verification email has been sent"));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> me() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> updateProfile(@RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(request));
    }

    private void rateLimitMfa(String challengeToken, HttpServletRequest request) {
        String account = mfaAuthenticationService.getChallengeAccountIdentifier(challengeToken);
        requestRateLimiter.checkMfa(account, clientAddressResolver.resolve(request));
    }
}
