package com.racetracker.service.impl;

import com.racetracker.dto.auth.MfaEnrollmentResponse;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.AuthToken;
import com.racetracker.model.MfaChallenge;
import com.racetracker.model.MfaRecoveryCode;
import com.racetracker.model.User;
import com.racetracker.model.UserMfaCredential;
import com.racetracker.repository.AuthTokenRepository;
import com.racetracker.repository.MfaChallengeRepository;
import com.racetracker.repository.MfaRecoveryCodeRepository;
import com.racetracker.repository.UserMfaCredentialRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.JwtService;
import com.racetracker.security.MfaSecretCipher;
import com.racetracker.security.TotpService;
import com.racetracker.service.AuditEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MfaAuthenticationServiceImpl {

    private static final String PURPOSE_LOGIN = "LOGIN";
    private static final String PURPOSE_ENROLL = "ENROLL";
    private static final int RECOVERY_CODE_COUNT = 10;

    private final UserRepository userRepository;
    private final UserMfaCredentialRepository credentialRepository;
    private final MfaChallengeRepository challengeRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final AuthTokenRepository authTokenRepository;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final MfaSecretCipher secretCipher;
    private final AuditEventService auditEventService;
    private final SecureRandom secureRandom = new SecureRandom();

    public MfaAuthenticationServiceImpl(
        UserRepository userRepository,
        UserMfaCredentialRepository credentialRepository,
        MfaChallengeRepository challengeRepository,
        MfaRecoveryCodeRepository recoveryCodeRepository,
        AuthTokenRepository authTokenRepository,
        JwtService jwtService,
        TotpService totpService,
        MfaSecretCipher secretCipher,
        AuditEventService auditEventService
    ) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.challengeRepository = challengeRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.authTokenRepository = authTokenRepository;
        this.jwtService = jwtService;
        this.totpService = totpService;
        this.secretCipher = secretCipher;
        this.auditEventService = auditEventService;
    }

    @Transactional
    public Challenge createChallenge(User user) {
        if (!isPrivileged(user.getRole())) {
            throw new IllegalArgumentException("MFA challenges are only issued for privileged accounts");
        }

        String purpose = credentialRepository.existsByUserIdAndVerifiedAtIsNotNull(user.getId())
            ? PURPOSE_LOGIN
            : PURPOSE_ENROLL;
        MfaChallenge challenge = new MfaChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setUserId(user.getId());
        challenge.setPurpose(purpose);
        challenge.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        challengeRepository.save(challenge);

        return new Challenge(
            jwtService.generateMfaChallengeToken(user, challenge.getId(), purpose),
            PURPOSE_ENROLL.equals(purpose)
        );
    }

    @Transactional
    public MfaEnrollmentResponse beginEnrollment(String challengeToken) {
        ChallengeState state = requireChallenge(challengeToken, PURPOSE_ENROLL);
        User user = userRepository.findById(state.userId())
            .orElseThrow(() -> new UnauthorizedException("Invalid MFA challenge"));
        UserMfaCredential credential = credentialRepository.findById(user.getId()).orElseGet(() -> {
            UserMfaCredential created = new UserMfaCredential();
            created.setUserId(user.getId());
            return created;
        });
        if (credential.getVerifiedAt() != null) {
            throw new ConflictException("MFA is already enabled");
        }

        String secret = totpService.newSecret();
        credential.setEncryptedSecret(secretCipher.encrypt(secret));
        credentialRepository.save(credential);
        return new MfaEnrollmentResponse(secret, totpService.provisioningUri(user.getEmail(), secret));
    }

    @Transactional
    public EnrollmentConfirmation confirmEnrollment(String challengeToken, String code) {
        ChallengeState state = requireChallenge(challengeToken, PURPOSE_ENROLL);
        User user = userRepository.findById(state.userId())
            .orElseThrow(() -> new UnauthorizedException("Invalid MFA challenge"));
        UserMfaCredential credential = credentialRepository.findById(user.getId())
            .orElseThrow(() -> new UnauthorizedException("Start MFA enrollment before confirming a code"));
        if (credential.getVerifiedAt() != null) {
            throw new ConflictException("MFA is already enabled");
        }

        verifyTotp(user, credential, code, "MFA_ENROLLMENT");
        consumeChallenge(state, PURPOSE_ENROLL);
        credential.setVerifiedAt(OffsetDateTime.now());
        credentialRepository.save(credential);
        auditEventService.recordInCurrentTransaction("MFA_ENROLLMENT", user.getId(), "USER", user.getId().toString(),
            "SUCCESS", java.util.Map.of());
        return new EnrollmentConfirmation(user.getId(), replaceRecoveryCodes(user.getId()));
    }

    @Transactional
    public UUID completeLogin(String challengeToken, String code) {
        ChallengeState state = requireChallenge(challengeToken, PURPOSE_LOGIN);
        User user = userRepository.findById(state.userId())
            .orElseThrow(() -> new UnauthorizedException("Invalid MFA challenge"));
        UserMfaCredential credential = credentialRepository.findByUserIdAndVerifiedAtIsNotNull(user.getId())
            .orElseThrow(() -> new UnauthorizedException("MFA enrollment is required"));

        boolean validTotp = isValidTotp(credential, code);
        MfaRecoveryCode recoveryCode = validTotp ? null : findRecoveryCode(user.getId(), code);
        if (!validTotp && recoveryCode == null) {
            auditEventService.record("MFA_LOGIN_CHALLENGE", user.getId(), "USER", user.getId().toString(),
                "DENIED", java.util.Map.of("reason", "invalid_code"));
            throw new UnauthorizedException("Invalid MFA code");
        }

        consumeChallenge(state, PURPOSE_LOGIN);
        if (recoveryCode != null && recoveryCodeRepository.markUsed(
            recoveryCode.getId(), user.getId(), OffsetDateTime.now()
        ) != 1) {
            auditEventService.record("MFA_LOGIN_CHALLENGE", user.getId(), "USER", user.getId().toString(),
                "DENIED", java.util.Map.of("reason", "recovery_code_replayed"));
            throw new UnauthorizedException("Invalid MFA code");
        }
        auditEventService.recordInCurrentTransaction("MFA_LOGIN_CHALLENGE", user.getId(), "USER", user.getId().toString(),
            "SUCCESS", java.util.Map.of("method", recoveryCode == null ? "totp" : "recovery_code"));
        return user.getId();
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(String email, String code) {
        User user = requireUser(email);
        UserMfaCredential credential = credentialRepository.findByUserIdAndVerifiedAtIsNotNull(user.getId())
            .orElseThrow(() -> new UnauthorizedException("MFA is not enabled"));
        verifyTotp(user, credential, code, "MFA_RECOVERY_CODES_REGENERATED");
        List<String> codes = replaceRecoveryCodes(user.getId());
        auditEventService.recordInCurrentTransaction("MFA_RECOVERY_CODES_REGENERATED", user.getId(), "USER", user.getId().toString(),
            "SUCCESS", java.util.Map.of());
        return codes;
    }

    @Transactional
    public void disable(String email, String code) {
        User user = requireUser(email);
        UserMfaCredential credential = credentialRepository.findByUserIdAndVerifiedAtIsNotNull(user.getId())
            .orElseThrow(() -> new UnauthorizedException("MFA is not enabled"));
        verifyTotp(user, credential, code, "MFA_DISABLED");
        removeMfa(user.getId());
        auditEventService.recordInCurrentTransaction("MFA_DISABLED", user.getId(), "USER", user.getId().toString(),
            "SUCCESS", java.util.Map.of());
    }

    @Transactional
    public void resetBySuperAdmin(UUID userId, String actorEmail) {
        if (!userRepository.existsById(userId)) {
            throw new com.racetracker.exception.ResourceNotFoundException("User not found");
        }
        User actor = requireUser(actorEmail);
        removeMfa(userId);
        auditEventService.recordInCurrentTransaction("MFA_ADMIN_RESET", actor.getId(), "USER", userId.toString(),
            "SUCCESS", java.util.Map.of());
    }

    @Transactional(readOnly = true)
    public boolean isPrivilegedSessionVerified(String email, String accessToken) {
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            return false;
        }
        if (!isPrivileged(user.getRole())) {
            return true;
        }
        return jwtService.isMfaVerified(accessToken)
            && credentialRepository.existsByUserIdAndVerifiedAtIsNotNull(user.getId());
    }

    @Transactional(readOnly = true)
    public boolean isMfaEnabled(UUID userId) {
        return credentialRepository.existsByUserIdAndVerifiedAtIsNotNull(userId);
    }

    @Transactional(readOnly = true)
    public String getChallengeAccountIdentifier(String challengeToken) {
        if (!jwtService.hasTokenType(challengeToken, "mfa_challenge")) {
            throw new UnauthorizedException("Invalid or expired MFA challenge");
        }
        try {
            String username = jwtService.extractUsername(challengeToken);
            if (username == null || username.isBlank()) {
                throw new UnauthorizedException("Invalid MFA challenge");
            }
            return username;
        } catch (RuntimeException ex) {
            if (ex instanceof UnauthorizedException unauthorized) throw unauthorized;
            throw new UnauthorizedException("Invalid or expired MFA challenge");
        }
    }

    public boolean isPrivileged(User.UserRole role) {
        return role == User.UserRole.SUPER_ADMIN
            || role == User.UserRole.RACE_ADMIN
            || role == User.UserRole.OFFICIAL
            || role == User.UserRole.VOLUNTEER;
    }

    private ChallengeState requireChallenge(String token, String expectedPurpose) {
        if (!jwtService.hasTokenType(token, "mfa_challenge")) {
            throw new UnauthorizedException("Invalid or expired MFA challenge");
        }
        try {
            UUID userId = jwtService.extractUserId(token);
            UUID challengeId = jwtService.extractMfaChallengeId(token);
            String purpose = jwtService.extractMfaPurpose(token);
            if (userId == null || challengeId == null || !expectedPurpose.equals(purpose)) {
                throw new UnauthorizedException("Invalid MFA challenge");
            }
            MfaChallenge challenge = challengeRepository.findByIdAndUserId(challengeId, userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired MFA challenge"));
            if (!expectedPurpose.equals(challenge.getPurpose())
                || challenge.getConsumedAt() != null
                || !challenge.getExpiresAt().isAfter(OffsetDateTime.now())) {
                throw new UnauthorizedException("Invalid or expired MFA challenge");
            }
            return new ChallengeState(userId, challengeId);
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedException("Invalid or expired MFA challenge");
        }
    }

    private void consumeChallenge(ChallengeState state, String purpose) {
        if (challengeRepository.consumeActive(
            state.challengeId(), state.userId(), purpose, OffsetDateTime.now()
        ) != 1) {
            throw new UnauthorizedException("MFA challenge has expired or was already used");
        }
    }

    private void verifyTotp(User user, UserMfaCredential credential, String code, String action) {
        if (!isValidTotp(credential, code)) {
            auditEventService.record(action, user.getId(), "USER", user.getId().toString(),
                "DENIED", java.util.Map.of("reason", "invalid_code"));
            throw new UnauthorizedException("Invalid MFA code");
        }
    }

    private boolean isValidTotp(UserMfaCredential credential, String code) {
        return code != null && totpService.verify(secretCipher.decrypt(credential.getEncryptedSecret()), code, Instant.now());
    }

    private MfaRecoveryCode findRecoveryCode(UUID userId, String rawCode) {
        if (rawCode == null || rawCode.length() > 64) {
            return null;
        }
        return recoveryCodeRepository.findByUserIdAndCodeHashAndUsedAtIsNull(userId, hashRecoveryCode(rawCode)).orElse(null);
    }

    private List<String> replaceRecoveryCodes(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        recoveryCodeRepository.invalidateAll(userId, now);
        List<String> rawCodes = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<MfaRecoveryCode> storedCodes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int index = 0; index < RECOVERY_CODE_COUNT; index++) {
            byte[] random = new byte[12];
            secureRandom.nextBytes(random);
            String raw = HexFormat.of().formatHex(random).toUpperCase(Locale.ROOT);
            String formatted = raw.substring(0, 6) + "-" + raw.substring(6, 12) + "-" + raw.substring(12, 18) + "-" + raw.substring(18);
            rawCodes.add(formatted);
            MfaRecoveryCode stored = new MfaRecoveryCode();
            stored.setId(UUID.randomUUID());
            stored.setUserId(userId);
            stored.setCodeHash(hashRecoveryCode(formatted));
            storedCodes.add(stored);
        }
        recoveryCodeRepository.saveAll(storedCodes);
        return List.copyOf(rawCodes);
    }

    private String hashRecoveryCode(String code) {
        try {
            String normalized = code.replace("-", "").trim().toUpperCase(Locale.ROOT);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash MFA recovery code", ex);
        }
    }

    private void removeMfa(UUID userId) {
        credentialRepository.deleteById(userId);
        recoveryCodeRepository.invalidateAll(userId, OffsetDateTime.now());
        List<AuthToken> refreshTokens = authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(
            userId, AuthToken.TokenType.REFRESH
        );
        if (!refreshTokens.isEmpty()) {
            OffsetDateTime now = OffsetDateTime.now();
            refreshTokens.forEach(token -> token.setConsumedAt(now));
            authTokenRepository.saveAll(refreshTokens);
        }
    }

    private User requireUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
    }

    public record Challenge(String token, boolean enrollmentRequired) { }
    public record EnrollmentConfirmation(UUID userId, List<String> recoveryCodes) { }
    private record ChallengeState(UUID userId, UUID challengeId) { }
}
