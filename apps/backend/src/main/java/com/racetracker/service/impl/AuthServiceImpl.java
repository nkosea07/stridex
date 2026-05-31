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

    public AuthServiceImpl(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtService jwtService,
        UserMapper userMapper,
        AuthTokenRepository authTokenRepository,
        AuthEmailService authEmailService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.authTokenRepository = authTokenRepository;
        this.authEmailService = authEmailService;
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

        return buildAuthResponse(saved);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user != null) {
            ensureAccountNotLocked(user);
            ensureRateLimitNotExceeded(user);
        }

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );
        } catch (Exception ex) {
            if (user != null) {
                recordFailedLogin(user);
            }
            throw new UnauthorizedException("Invalid credentials");
        }

        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        }

        resetFailedLoginState(user);
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtService.hasTokenType(refreshToken, JwtService.TOKEN_TYPE_REFRESH)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        String email;
        try {
            email = jwtService.extractUsername(refreshToken);
        } catch (Exception ex) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        org.springframework.security.core.userdetails.UserDetails userDetails =
            new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                java.util.List.of()
            );

        if (!jwtService.isTokenValid(refreshToken, userDetails)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        return buildAuthResponse(user);
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

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.builder()
            .token(jwtService.generateAccessToken(user))
            .refreshToken(jwtService.generateRefreshToken(user))
            .user(userMapper.toDto(user))
            .build();
    }
}
