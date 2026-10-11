package com.racetracker.service.impl;

import com.racetracker.dto.auth.*;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.TooManyRequestsException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.mapper.UserMapper;
import com.racetracker.model.AuthToken;
import com.racetracker.model.User;
import com.racetracker.repository.AuthTokenRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.JwtService;
import com.racetracker.service.AuthEmailService;
import com.racetracker.service.AuthService;
import com.racetracker.service.AuditEventService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int MAX_ATTEMPTS_PER_WINDOW = 10;
    private static final Duration FAILED_ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final Duration ACCOUNT_LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofHours(1);
    private static final Duration EMAIL_VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AuthTokenRepository authTokenRepository;
    private final AuthEmailService authEmailService;
    private final MfaAuthenticationServiceImpl mfaAuthenticationService;
    private final AuditEventService auditEventService;

    public AuthServiceImpl(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtService jwtService,
        UserMapper userMapper,
        AuthTokenRepository authTokenRepository,
        AuthEmailService authEmailService,
        MfaAuthenticationServiceImpl mfaAuthenticationService,
        AuditEventService auditEventService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.authTokenRepository = authTokenRepository;
        this.authEmailService = authEmailService;
        this.mfaAuthenticationService = mfaAuthenticationService;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Email already exists");
        }

        User user = User.builder()
            .email(normalizedEmail)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName().trim())
            .lastName(request.getLastName().trim())
            .phone(request.getPhone())
            .role(User.UserRole.ATHLETE)
            .isActive(true)
            .emailVerified(false)
            .failedLoginAttempts(0)
            .build();

        User saved = userRepository.save(user);
        issueEmailVerificationToken(saved);

        return buildVerificationRequiredResponse(saved);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user != null) {
            ensureAccountNotLocked(user);
            ensureRateLimitNotExceeded(user);
            ensureEmailVerified(user);
        }

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );
        } catch (Exception ex) {
            if (user != null) {
                recordFailedLogin(user);
                if (mfaAuthenticationService.isPrivileged(user.getRole())) {
                    auditEventService.record("PRIVILEGED_LOGIN", user.getId(), "USER", user.getId().toString(),
                        "DENIED", java.util.Map.of("method", "password"));
                }
            }
            throw new UnauthorizedException("Invalid credentials");
        }

        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        }

        resetFailedLoginState(user);
        if (mfaAuthenticationService.isPrivileged(user.getRole())) {
            MfaAuthenticationServiceImpl.Challenge challenge = mfaAuthenticationService.createChallenge(user);
            return AuthResponse.builder()
                .requiresEmailVerification(false)
                .mfaRequired(true)
                .mfaEnrollmentRequired(challenge.enrollmentRequired())
                .mfaChallengeToken(challenge.token())
                .user(userMapper.toDto(user))
                .build();
        }
        return buildAuthenticatedResponse(user, false);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtService.hasTokenType(refreshToken, JwtService.TOKEN_TYPE_REFRESH)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        AuthToken storedToken = authTokenRepository.findByTokenAndTokenTypeAndConsumedAtIsNull(
                refreshToken,
                AuthToken.TokenType.REFRESH
            )
            .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (storedToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            storedToken.setConsumedAt(OffsetDateTime.now());
            authTokenRepository.save(storedToken);
            throw new UnauthorizedException("Invalid refresh token");
        }

        User user = storedToken.getUser();
        ensureUserCanReceiveSession(user);
        boolean mfaVerified = mfaAuthenticationService.isPrivileged(user.getRole());
        if (mfaVerified && (!jwtService.isMfaVerified(refreshToken) || !mfaAuthenticationService.isMfaEnabled(user.getId()))) {
            throw new UnauthorizedException("MFA verification is required");
        }

        org.springframework.security.core.userdetails.UserDetails userDetails = new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPasswordHash(),
            Boolean.TRUE.equals(user.getIsActive()) && Boolean.TRUE.equals(user.getEmailVerified()),
            true,
            true,
            true,
            java.util.List.of()
        );

        if (!jwtService.isTokenValid(refreshToken, userDetails)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        storedToken.setConsumedAt(OffsetDateTime.now());
        authTokenRepository.save(storedToken);
        return buildAuthenticatedResponse(user, mfaVerified);
    }

    @Override
    @Transactional
    public AuthResponse issueMfaSession(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("Invalid MFA challenge"));
        ensureUserCanReceiveSession(user);
        if (!mfaAuthenticationService.isPrivileged(user.getRole()) || !mfaAuthenticationService.isMfaEnabled(userId)) {
            throw new UnauthorizedException("MFA verification is required");
        }
        auditEventService.recordInCurrentTransaction("PRIVILEGED_LOGIN", user.getId(), "USER", user.getId().toString(),
            "SUCCESS", java.util.Map.of("method", "mfa"));
        return buildAuthenticatedResponse(user, true);
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        authTokenRepository.findByTokenAndTokenTypeAndConsumedAtIsNull(
            refreshToken, AuthToken.TokenType.REFRESH
        ).ifPresent(token -> {
            token.setConsumedAt(OffsetDateTime.now());
            authTokenRepository.save(token);
        });
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            invalidateOutstandingTokens(user.getId(), AuthToken.TokenType.PASSWORD_RESET);
            AuthToken token = createToken(user, AuthToken.TokenType.PASSWORD_RESET, PASSWORD_RESET_TOKEN_TTL);
            authTokenRepository.save(token);
            authEmailService.sendPasswordReset(user, token.getToken());
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        AuthToken token = consumeValidToken(request.getToken(), AuthToken.TokenType.PASSWORD_RESET);
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        resetFailedLoginState(user);
        invalidateOutstandingTokens(user.getId(), AuthToken.TokenType.REFRESH);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void verifyEmail(String tokenValue) {
        AuthToken token = consumeValidToken(tokenValue, AuthToken.TokenType.EMAIL_VERIFICATION);
        User user = token.getUser();
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }
    }

    @Override
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            if (Boolean.TRUE.equals(user.getEmailVerified())) {
                return;
            }
            issueEmailVerificationToken(user);
        });
    }

    @Override
    public UserDto getCurrentUser() {
        User user = resolveCurrentUser();
        return userMapper.toDto(user);
    }

    @Override
    @Transactional
    public UserDto updateProfile(UpdateProfileRequest request) {
        User user = resolveCurrentUser();
        userMapper.updateProfile(request, user);
        User saved = userRepository.save(user);
        return userMapper.toDto(saved);
    }

    private void issueEmailVerificationToken(User user) {
        invalidateOutstandingTokens(user.getId(), AuthToken.TokenType.EMAIL_VERIFICATION);
        AuthToken token = createToken(user, AuthToken.TokenType.EMAIL_VERIFICATION, EMAIL_VERIFICATION_TOKEN_TTL);
        authTokenRepository.save(token);
        authEmailService.sendEmailVerification(user, token.getToken());
    }

    private AuthToken createToken(User user, AuthToken.TokenType type, Duration ttl) {
        return AuthToken.builder()
            .user(user)
            .token(UUID.randomUUID().toString().replace("-", ""))
            .tokenType(type)
            .expiresAt(OffsetDateTime.now().plus(ttl))
            .build();
    }

    private AuthToken createRefreshToken(User user, String refreshToken) {
        return AuthToken.builder()
            .user(user)
            .token(refreshToken)
            .tokenType(AuthToken.TokenType.REFRESH)
            .expiresAt(jwtService.extractExpiration(refreshToken))
            .build();
    }

    private AuthToken consumeValidToken(String rawToken, AuthToken.TokenType expectedType) {
        AuthToken authToken = authTokenRepository.findByToken(rawToken)
            .orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));

        if (authToken.getTokenType() != expectedType) {
            throw new UnauthorizedException("Invalid or expired token");
        }

        if (authToken.getConsumedAt() != null) {
            throw new UnauthorizedException("Invalid or expired token");
        }

        if (authToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new UnauthorizedException("Invalid or expired token");
        }

        authToken.setConsumedAt(OffsetDateTime.now());
        return authTokenRepository.save(authToken);
    }

    private void invalidateOutstandingTokens(UUID userId, AuthToken.TokenType tokenType) {
        List<AuthToken> active = authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(userId, tokenType);
        OffsetDateTime now = OffsetDateTime.now();
        for (AuthToken token : active) {
            token.setConsumedAt(now);
        }
        if (!active.isEmpty()) {
            authTokenRepository.saveAll(active);
        }
    }

    private void ensureEmailVerified(User user) {
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new UnauthorizedException("Email verification required");
        }
    }

    private void ensureUserCanReceiveSession(User user) {
        if (!Boolean.TRUE.equals(user.getIsActive()) || !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new UnauthorizedException("Invalid refresh token");
        }
    }

    private void ensureAccountNotLocked(User user) {
        OffsetDateTime lockoutUntil = user.getLockoutUntil();
        if (lockoutUntil != null && lockoutUntil.isAfter(OffsetDateTime.now())) {
            throw new TooManyRequestsException("Account temporarily locked due to multiple failed login attempts");
        }
    }

    private void ensureRateLimitNotExceeded(User user) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime lastFailed = user.getLastFailedLoginAt();
        Integer failedAttempts = user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();

        if (lastFailed != null && lastFailed.isAfter(now.minus(FAILED_ATTEMPT_WINDOW)) && failedAttempts >= MAX_ATTEMPTS_PER_WINDOW) {
            throw new TooManyRequestsException("Too many login attempts. Please try again later");
        }
    }

    private void recordFailedLogin(User user) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime lastFailed = user.getLastFailedLoginAt();

        int failedAttempts = user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
        if (lastFailed == null || lastFailed.isBefore(now.minus(FAILED_ATTEMPT_WINDOW))) {
            failedAttempts = 0;
        }

        failedAttempts += 1;
        user.setFailedLoginAttempts(failedAttempts);
        user.setLastFailedLoginAt(now);

        if (failedAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            user.setLockoutUntil(now.plus(ACCOUNT_LOCK_DURATION));
        }

        userRepository.save(user);
    }

    private void resetFailedLoginState(User user) {
        boolean changed = false;

        if (user.getFailedLoginAttempts() == null || user.getFailedLoginAttempts() != 0) {
            user.setFailedLoginAttempts(0);
            changed = true;
        }
        if (user.getLastFailedLoginAt() != null) {
            user.setLastFailedLoginAt(null);
            changed = true;
        }
        if (user.getLockoutUntil() != null) {
            user.setLockoutUntil(null);
            changed = true;
        }

        if (changed) {
            userRepository.save(user);
        }
    }

    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        return userRepository.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse buildVerificationRequiredResponse(User user) {
        return AuthResponse.builder()
            .token(null)
            .refreshToken(null)
            .requiresEmailVerification(true)
            .user(userMapper.toDto(user))
            .build();
    }

    private AuthResponse buildAuthenticatedResponse(User user, boolean mfaVerified) {
        invalidateOutstandingTokens(user.getId(), AuthToken.TokenType.REFRESH);
        String refreshToken = mfaVerified
            ? jwtService.generateRefreshToken(user, true)
            : jwtService.generateRefreshToken(user);
        authTokenRepository.save(createRefreshToken(user, refreshToken));

        return AuthResponse.builder()
            .token(mfaVerified ? jwtService.generateAccessToken(user, true) : jwtService.generateAccessToken(user))
            .refreshToken(refreshToken)
            .requiresEmailVerification(false)
            .user(userMapper.toDto(user))
            .build();
    }
}
