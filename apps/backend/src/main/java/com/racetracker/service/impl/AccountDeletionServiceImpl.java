package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.privacy.DeleteAccountRequest;
import com.racetracker.dto.privacy.DeletionStatusDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.User;
import com.racetracker.repository.AuthTokenRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.AccountDeletionService;
import com.racetracker.service.AccountPrivacyService;
import com.racetracker.service.AuditEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Self-service account deletion: re-authenticate, schedule a grace window, allow cancellation,
 * and hand off the irreversible erasure to {@link AccountPrivacyService} when the window lapses.
 */
@Slf4j
@Service
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private static final String CONFIRMATION_PHRASE = "DELETE";

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventService auditEventService;
    private final AccountPrivacyService accountPrivacyService;
    private final RaceTrackerProperties properties;

    public AccountDeletionServiceImpl(
        UserRepository userRepository,
        AuthTokenRepository authTokenRepository,
        PasswordEncoder passwordEncoder,
        AuditEventService auditEventService,
        AccountPrivacyService accountPrivacyService,
        RaceTrackerProperties properties
    ) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditEventService = auditEventService;
        this.accountPrivacyService = accountPrivacyService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public DeletionStatusDto request(UUID userId, DeleteAccountRequest request) {
        User user = requireUser(userId);
        if (user.getDeletedAt() != null) {
            throw new ConflictException("This account has already been deleted");
        }
        if (user.getDeletionRequestedAt() != null) {
            return toStatus(user);
        }
        if (request == null || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ApiException("Your password is required to delete your account");
        }
        if (user.getPasswordHash() == null
            || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Password is incorrect");
        }
        if (request.getConfirmation() == null
            || !CONFIRMATION_PHRASE.equalsIgnoreCase(request.getConfirmation().trim())) {
            throw new ApiException("Type " + CONFIRMATION_PHRASE + " to confirm account deletion");
        }

        int graceDays = Math.max(properties.getPrivacy().getDeletionGraceDays(), 0);
        OffsetDateTime now = OffsetDateTime.now();
        user.setDeletionRequestedAt(now);
        user.setDeletionScheduledFor(now.plusDays(graceDays));
        user.setIsActive(false);
        userRepository.save(user);

        authTokenRepository.deleteByUserId(userId);

        auditEventService.recordInCurrentTransaction("ACCOUNT_DELETION_REQUESTED", userId, "USER", userId.toString(),
            "SUCCESS", Map.of("scheduledFor", user.getDeletionScheduledFor().toString()));
        return toStatus(user);
    }

    @Override
    @Transactional
    public DeletionStatusDto cancel(UUID userId) {
        User user = requireUser(userId);
        if (user.getDeletedAt() != null) {
            throw new ConflictException("This account has already been deleted");
        }
        if (user.getDeletionRequestedAt() != null) {
            user.setDeletionRequestedAt(null);
            user.setDeletionScheduledFor(null);
            user.setIsActive(true);
            userRepository.save(user);
            auditEventService.recordInCurrentTransaction("ACCOUNT_DELETION_CANCELLED", userId, "USER",
                userId.toString(), "SUCCESS", Map.of());
        }
        return toStatus(user);
    }

    @Override
    @Transactional(readOnly = true)
    public DeletionStatusDto status(UUID userId) {
        return toStatus(requireUser(userId));
    }

    @Override
    @Transactional
    public int purgeExpired() {
        List<User> due = userRepository.findDueForPurge(OffsetDateTime.now());
        for (User user : due) {
            accountPrivacyService.eraseAccount(user.getId());
        }
        return due.size();
    }

    @Scheduled(
        fixedDelayString = "${racetracker.privacy.deletion-scan-interval-millis:3600000}",
        initialDelayString = "${racetracker.privacy.deletion-initial-delay-millis:60000}"
    )
    public void scheduledPurge() {
        try {
            int purged = purgeExpired();
            if (purged > 0) {
                log.info("Account deletion purge erased {} account(s)", purged);
            }
        } catch (RuntimeException ex) {
            log.error("Account deletion purge failed", ex);
        }
    }

    private DeletionStatusDto toStatus(User user) {
        DeletionStatusDto dto = new DeletionStatusDto();
        dto.setDeletionRequested(user.getDeletionRequestedAt() != null);
        dto.setRequestedAt(user.getDeletionRequestedAt());
        dto.setScheduledFor(user.getDeletionScheduledFor());
        return dto;
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
