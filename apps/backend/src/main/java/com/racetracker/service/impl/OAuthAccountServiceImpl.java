package com.racetracker.service.impl;

import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.mapper.UserMapper;
import com.racetracker.model.AuthToken;
import com.racetracker.model.User;
import com.racetracker.repository.AuthTokenRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.JwtService;
import com.racetracker.service.OAuthAccountService;
import com.racetracker.service.OAuthIdentity;
import com.racetracker.service.OAuthIdentityVerifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OAuthAccountServiceImpl implements OAuthAccountService {

    private final OAuthIdentityVerifier identityVerifier;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AuthTokenRepository authTokenRepository;

    public OAuthAccountServiceImpl(
        OAuthIdentityVerifier identityVerifier,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        UserMapper userMapper,
        AuthTokenRepository authTokenRepository
    ) {
        this.identityVerifier = identityVerifier;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.authTokenRepository = authTokenRepository;
    }

    @Override
    @Transactional
    public AuthResponse authenticateWithGoogle(String idToken) {
        OAuthIdentity identity = identityVerifier.verifyGoogle(idToken);
        if (!identity.emailVerified()) {
            throw new UnauthorizedException("Google account email is not verified");
        }

        User user = userRepository.findByEmailIgnoreCase(identity.email())
            .orElseGet(() -> createUser(identity));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Account is disabled");
        }
        if (isPrivileged(user.getRole())) {
            // Privileged accounts must complete MFA; OAuth cannot satisfy that challenge.
            throw new UnauthorizedException("Privileged accounts must sign in with password and MFA");
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }

        return issueSession(user);
    }

    private User createUser(OAuthIdentity identity) {
        User user = User.builder()
            .email(identity.email())
            .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
            .firstName(identity.firstName() == null || identity.firstName().isBlank() ? "Athlete" : identity.firstName())
            .lastName(identity.lastName() == null ? "" : identity.lastName())
            .role(User.UserRole.ATHLETE)
            .isActive(true)
            .emailVerified(true)
            .failedLoginAttempts(0)
            .build();
        return userRepository.save(user);
    }

    private AuthResponse issueSession(User user) {
        List<AuthToken> active = authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(
            user.getId(), AuthToken.TokenType.REFRESH);
        OffsetDateTime now = OffsetDateTime.now();
        for (AuthToken token : active) {
            token.setConsumedAt(now);
        }
        if (!active.isEmpty()) {
            authTokenRepository.saveAll(active);
        }

        String refreshToken = jwtService.generateRefreshToken(user);
        authTokenRepository.save(AuthToken.builder()
            .user(user)
            .token(refreshToken)
            .tokenType(AuthToken.TokenType.REFRESH)
            .expiresAt(jwtService.extractExpiration(refreshToken))
            .build());

        return AuthResponse.builder()
            .token(jwtService.generateAccessToken(user))
            .refreshToken(refreshToken)
            .requiresEmailVerification(false)
            .user(userMapper.toDto(user))
            .build();
    }

    private boolean isPrivileged(User.UserRole role) {
        return role == User.UserRole.SUPER_ADMIN
            || role == User.UserRole.RACE_ADMIN
            || role == User.UserRole.OFFICIAL
            || role == User.UserRole.VOLUNTEER;
    }
}
