package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.privacy.DeleteAccountRequest;
import com.racetracker.dto.privacy.DeletionStatusDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.AuthTokenRepository;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.repository.RaceRepository;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Self-service account deletion: re-authenticate, schedule a grace window, allow cancellation,
 * and hand off the irreversible erasure to {@link AccountPrivacyService} when the window lapses.
 */
@Slf4j
@Service
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private static final String CONFIRMATION_PHRASE = "DELETE";
    static final long DELETION_REQUEST_LOCK_KEY = 4_207_110_001L;
    static final String BLOCKER_LAST_SUPER_ADMIN = "ACCOUNT_DELETION_LAST_SUPER_ADMIN";
    static final String BLOCKER_ACTIVE_RACES = "ACCOUNT_DELETION_ACTIVE_RACES";
    private static final Set<Race.RaceStatus> ACTIVE_RACE_STATUSES = EnumSet.of(
        Race.RaceStatus.REGISTRATION_OPEN,
        Race.RaceStatus.REGISTRATION_CLOSED,
        Race.RaceStatus.IN_PROGRESS
    );

    private final UserRepository userRepository;
    private final RaceRepository raceRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventService auditEventService;
    private final AccountPrivacyService accountPrivacyService;
    private final RaceTrackerProperties properties;
    private final com.racetracker.service.AuthEmailService authEmailService;

    public AccountDeletionServiceImpl(
        UserRepository userRepository,
        RaceRepository raceRepository,
        AuthTokenRepository authTokenRepository,
        PushDeviceRepository pushDeviceRepository,
        PasswordEncoder passwordEncoder,
        AuditEventService auditEventService,
        AccountPrivacyService accountPrivacyService,
        RaceTrackerProperties properties,
        com.racetracker.service.AuthEmailService authEmailService
    ) {
        this.userRepository = userRepository;
        this.raceRepository = raceRepository;
        this.authTokenRepository = authTokenRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditEventService = auditEventService;
        this.accountPrivacyService = accountPrivacyService;
        this.properties = properties;
        this.authEmailService = authEmailService;
    }

    @Override
    @Transactional
    public DeletionStatusDto request(UUID userId, DeleteAccountRequest request) {
        userRepository.acquireTransactionLock(DELETION_REQUEST_LOCK_KEY);
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getDeletedAt() != null) {
            throw new ConflictException("This account has already been deleted", "ACCOUNT_ALREADY_DELETED");
        }
        if (user.getDeletionRequestedAt() != null) {
            return toStatus(user);
        }
        if (request == null || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ApiException("Your password is required to delete your account",
                "ACCOUNT_DELETION_PASSWORD_REQUIRED");
        }
        if (user.getPasswordHash() == null
            || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException("Password is incorrect", "ACCOUNT_DELETION_PASSWORD_INVALID");
        }
        if (request.getConfirmation() == null
            || !CONFIRMATION_PHRASE.equalsIgnoreCase(request.getConfirmation().trim())) {
            throw new ApiException("Type " + CONFIRMATION_PHRASE + " to confirm account deletion",
                "ACCOUNT_DELETION_CONFIRMATION_REQUIRED");
        }
        List<String> blockers = blockers(user);
        if (!blockers.isEmpty()) {
            throw new ConflictException("Account deletion is blocked until account responsibilities are handed over",
                blockers.get(0));
        }

        int graceDays = Math.max(properties.getPrivacy().getDeletionGraceDays(), 0);
        OffsetDateTime now = OffsetDateTime.now();
        user.setDeletionRequestedAt(now);
        user.setDeletionScheduledFor(now.plusDays(graceDays));
        user.setIsActive(false);
        userRepository.save(user);

        authTokenRepository.deleteByUserId(userId);
        pushDeviceRepository.deleteByUserId(userId);

        auditEventService.recordInCurrentTransaction("ACCOUNT_DELETION_REQUESTED", userId, "USER", userId.toString(),
            "SUCCESS", Map.of("scheduledFor", user.getDeletionScheduledFor().toString()));
        OffsetDateTime scheduledFor = user.getDeletionScheduledFor();
        afterCommit(() -> authEmailService.sendAccountDeletionScheduled(user, scheduledFor));
        return toStatus(user);
    }

    @Override
    @Transactional
    public DeletionStatusDto cancel(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getDeletedAt() != null) {
            throw new ConflictException("This account has already been deleted", "ACCOUNT_ALREADY_DELETED");
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
        User user = requireUser(userId);
        DeletionStatusDto status = toStatus(user);
        if (user.getDeletionRequestedAt() == null && user.getDeletedAt() == null) {
            status.setBlockers(blockers(user));
        }
        return status;
    }

    private List<String> blockers(User user) {
        List<String> blockers = new ArrayList<>();
        if (user.getRole() == User.UserRole.SUPER_ADMIN
            && userRepository.countOtherActiveByRole(User.UserRole.SUPER_ADMIN, user.getId()) == 0) {
            blockers.add(BLOCKER_LAST_SUPER_ADMIN);
        }
        if (raceRepository.countActiveWithoutOtherManager(user.getId(), ACTIVE_RACE_STATUSES) > 0) {
            blockers.add(BLOCKER_ACTIVE_RACES);
        }
        return List.copyOf(blockers);
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

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
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
