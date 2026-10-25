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
import org.springframework.data.domain.PageRequest;
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
    private final com.racetracker.service.AuthEmailService authEmailService;

    public AccountDeletionServiceImpl(
        UserRepository userRepository,
        AuthTokenRepository authTokenRepository,
        PasswordEncoder passwordEncoder,
        AuditEventService auditEventService,
        AccountPrivacyService accountPrivacyService,
        RaceTrackerProperties properties,
        com.racetracker.service.AuthEmailService authEmailService
    ) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditEventService = auditEventService;
        this.accountPrivacyService = accountPrivacyService;
        this.properties = properties;
        this.authEmailService = authEmailService;
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
        authEmailService.sendAccountDeletionScheduled(user, user.getDeletionScheduledFor());
        return toStatus(user);
    }

    @Override
    @Transactional
    public DeletionStatusDto cancel(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getDeletedAt() != null) {
            throw new ConflictException("This account has already been deleted");
        }
        if (user.getDeletionRequestedAt() != null && user.getDeletionScheduledFor() != null
            && user.getDeletionScheduledFor().isAfter(OffsetDateTime.now())) {
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
    @Transactional
    public void cancelWithPassword(String email, String password) {
        User user = userRepository.findByEmailIgnoreCaseForUpdate(email).orElse(null);
        if (user == null || user.getPasswordHash() == null
            || !passwordEncoder.matches(password, user.getPasswordHash())
            || user.getDeletedAt() != null || user.getDeletionRequestedAt() == null
            || user.getDeletionScheduledFor() == null
            || !user.getDeletionScheduledFor().isAfter(OffsetDateTime.now())) {
            return;
        }
        user.setDeletionRequestedAt(null);
        user.setDeletionScheduledFor(null);
        user.setIsActive(true);
        userRepository.save(user);
        auditEventService.recordInCurrentTransaction("ACCOUNT_DELETION_CANCELLED", user.getId(), "USER",
            user.getId().toString(), "SUCCESS", Map.of("method", "PASSWORD"));
    }

    @Override
    @Transactional(readOnly = true)
    public DeletionStatusDto status(UUID userId) {
        return toStatus(requireUser(userId));
    }

    @Override
    public int purgeExpired() {
        OffsetDateTime cutoff = OffsetDateTime.now();
        List<User> due = userRepository.findDueForPurge(cutoff, PageRequest.of(0, 50));
        int staged = 0;
        while (!due.isEmpty()) {
            for (User user : due) {
                try {
                    accountPrivacyService.eraseAccount(user.getId());
                    staged++;
                } catch (RuntimeException ex) {
                    log.error("Account erasure staging failed; userId={}", user.getId(), ex);
                }
            }
            User last = due.get(due.size() - 1);
            due = userRepository.findDueForPurgeAfter(cutoff, last.getDeletionScheduledFor(), last.getId(), 50);
        }
        return staged;
    }

    @Scheduled(
        fixedDelayString = "${racetracker.privacy.deletion-scan-interval-millis:3600000}",
        initialDelayString = "${racetracker.privacy.deletion-initial-delay-millis:60000}"
    )
    public void scheduledPurge() {
        try {
            int purged = purgeExpired();
            if (purged > 0) {
                log.info("Account deletion purge staged {} account(s) for object cleanup", purged);
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
