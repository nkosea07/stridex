package com.racetracker.controller;

import com.racetracker.config.CorsProperties;
import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.dto.auth.LoginRequest;
import com.racetracker.dto.auth.MfaCodeRequest;
import com.racetracker.dto.auth.MfaEnrollmentRequest;
import com.racetracker.dto.auth.MfaEnrollmentResponse;
import com.racetracker.service.AuthService;
import com.racetracker.service.impl.MfaAuthenticationServiceImpl;
import com.racetracker.security.ClientAddressResolver;
import com.racetracker.security.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Arrays;

@RestController
@RequestMapping("/v${racetracker.api.version}/auth/browser")
public class BrowserAuthController {

    private static final String REFRESH_COOKIE = "rt_browser_refresh";
    private static final String CSRF_COOKIE = "rt_browser_csrf";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth/browser";
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private final AuthService authService;
    private final MfaAuthenticationServiceImpl mfaAuthenticationService;
    private final CorsProperties corsProperties;
    private final boolean secureCookies;
    private final RequestRateLimiter requestRateLimiter;
    private final ClientAddressResolver clientAddressResolver;
    private final SecureRandom secureRandom = new SecureRandom();

    public BrowserAuthController(
        AuthService authService,
        MfaAuthenticationServiceImpl mfaAuthenticationService,
        CorsProperties corsProperties,
        @Value("${racetracker.auth.browser-cookie-secure:false}") boolean secureCookies,
        RequestRateLimiter requestRateLimiter,
        ClientAddressResolver clientAddressResolver
    ) {
        this.authService = authService;
        this.mfaAuthenticationService = mfaAuthenticationService;
        this.corsProperties = corsProperties;
        this.secureCookies = secureCookies;
        this.requestRateLimiter = requestRateLimiter;
        this.clientAddressResolver = clientAddressResolver;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        requireAllowedOrigin(servletRequest);
        requestRateLimiter.checkLogin(request.getEmail(), clientAddressResolver.resolve(servletRequest));
        return ResponseEntity.ok(completeSession(authService.login(request), servletResponse));
    }

    @PostMapping("/mfa/totp/enroll")
    public ResponseEntity<MfaEnrollmentResponse> enroll(
        @Valid @RequestBody MfaEnrollmentRequest request,
        HttpServletRequest servletRequest
    ) {
        requireAllowedOrigin(servletRequest);
        rateLimitMfa(request.getChallengeToken(), servletRequest);
        return ResponseEntity.ok(mfaAuthenticationService.beginEnrollment(request.getChallengeToken()));
    }

    @PostMapping("/mfa/totp/confirm")
    public ResponseEntity<AuthResponse> confirmEnrollment(
        @Valid @RequestBody MfaCodeRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        requireAllowedOrigin(servletRequest);
        rateLimitMfa(request.getChallengeToken(), servletRequest);
        var confirmation = mfaAuthenticationService.confirmEnrollment(request.getChallengeToken(), request.getCode());
        AuthResponse response = authService.issueMfaSession(confirmation.userId());
        response.setRecoveryCodes(confirmation.recoveryCodes());
        return ResponseEntity.ok(completeSession(response, servletResponse));
    }

    @PostMapping("/mfa/verify-login")
    public ResponseEntity<AuthResponse> verifyLogin(
        @Valid @RequestBody MfaCodeRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        requireAllowedOrigin(servletRequest);
        rateLimitMfa(request.getChallengeToken(), servletRequest);
        AuthResponse response = authService.issueMfaSession(
            mfaAuthenticationService.completeLogin(request.getChallengeToken(), request.getCode())
        );
        return ResponseEntity.ok(completeSession(response, servletResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        requireCsrfProtectedOrigin(request);
        String refreshToken = readCookie(request, REFRESH_COOKIE);
        if (refreshToken == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(completeSession(authService.refreshToken(refreshToken), response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        requireCsrfProtectedOrigin(request);
        authService.revokeRefreshToken(readCookie(request, REFRESH_COOKIE));
        clearCookies(response);
        return ResponseEntity.noContent().build();
    }

    private AuthResponse completeSession(AuthResponse response, HttpServletResponse servletResponse) {
        if (response.getRefreshToken() == null) {
            return response;
        }

        String refreshToken = response.getRefreshToken();
        byte[] csrfBytes = new byte[32];
        secureRandom.nextBytes(csrfBytes);
        String csrfToken = Base64.getUrlEncoder().withoutPadding().encodeToString(csrfBytes);
        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
            .httpOnly(true)
            .secure(secureCookies)
            .sameSite("Lax")
            .path(REFRESH_COOKIE_PATH)
            .maxAge(REFRESH_TTL)
            .build();
        ResponseCookie csrfCookie = ResponseCookie.from(CSRF_COOKIE, csrfToken)
            .httpOnly(false)
            .secure(secureCookies)
            .sameSite("Lax")
            .path("/")
            .maxAge(REFRESH_TTL)
            .build();
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
        response.setRefreshToken(null);
        return response;
    }

    private void clearCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true).secure(secureCookies).sameSite("Lax").path(REFRESH_COOKIE_PATH).maxAge(Duration.ZERO).build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(CSRF_COOKIE, "")
            .httpOnly(false).secure(secureCookies).sameSite("Lax").path("/").maxAge(Duration.ZERO).build().toString());
    }

    private void requireCsrfProtectedOrigin(HttpServletRequest request) {
        requireAllowedOrigin(request);
        String cookie = readCookie(request, CSRF_COOKIE);
        String header = request.getHeader("X-CSRF-Token");
        if (cookie == null || header == null || !MessageDigest.isEqual(
            cookie.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new com.racetracker.exception.UnauthorizedException("CSRF validation failed");
        }
    }

    private void requireAllowedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || corsProperties.getAllowedOrigins().stream().noneMatch(allowed -> allowed.equals(origin))) {
            throw new com.racetracker.exception.UnauthorizedException("Request origin is not allowed");
        }
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
            .filter(cookie -> name.equals(cookie.getName()))
            .map(jakarta.servlet.http.Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    private void rateLimitMfa(String challengeToken, HttpServletRequest request) {
        String account = mfaAuthenticationService.getChallengeAccountIdentifier(challengeToken);
        requestRateLimiter.checkMfa(account, clientAddressResolver.resolve(request));
    }
}
