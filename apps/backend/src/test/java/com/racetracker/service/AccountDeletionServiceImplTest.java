package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.privacy.DeleteAccountRequest;
import com.racetracker.dto.privacy.DeletionStatusDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.model.User;
import com.racetracker.repository.AuthTokenRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.AccountDeletionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceImplTest {

    @Mock com.racetracker.repository.PushDeviceRepository pushDeviceRepository;
    @Mock UserRepository userRepository;
    @Mock RaceRepository raceRepository;
    @Mock AuthTokenRepository authTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditEventService auditEventService;
    @Mock AccountPrivacyService accountPrivacyService;
    @Mock AuthEmailService authEmailService;

    private AccountDeletionServiceImpl service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getPrivacy().setDeletionGraceDays(30);
        service = new AccountDeletionServiceImpl(userRepository, raceRepository, authTokenRepository, pushDeviceRepository, passwordEncoder,
            auditEventService, accountPrivacyService, properties, authEmailService);
    }

    private User user() {
        return User.builder().id(userId).email("runner@example.com").passwordHash("hash")
            .isActive(true).emailVerified(true).build();
    }

    private DeleteAccountRequest request(String password, String confirmation) {
        DeleteAccountRequest request = new DeleteAccountRequest();
        request.setPassword(password);
        request.setConfirmation(confirmation);
        return request;
    }

    @Test
    void requestSchedulesDeletionAndDeactivates() {
        User user = user();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        DeletionStatusDto status = service.request(userId, request("pw", "DELETE"));

        assertThat(status.isDeletionRequested()).isTrue();
        assertThat(status.getScheduledFor()).isAfter(OffsetDateTime.now().plusDays(29));
        assertThat(user.getIsActive()).isFalse();
        verify(userRepository).save(user);
        verify(authTokenRepository).deleteByUserId(userId);
        verify(pushDeviceRepository).deleteByUserId(userId);
        verify(authEmailService).sendAccountDeletionScheduled(user, user.getDeletionScheduledFor());
    }

    @Test
    void requestRejectsWrongPassword() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("nope", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.request(userId, request("nope", "DELETE")))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("ACCOUNT_DELETION_PASSWORD_INVALID");
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void lastActiveSuperAdminCannotScheduleDeletion() {
        User admin = user();
        admin.setRole(User.UserRole.SUPER_ADMIN);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(userRepository.countOtherActiveByRole(User.UserRole.SUPER_ADMIN, userId)).thenReturn(0L);

        assertThatThrownBy(() -> service.request(userId, request("pw", "DELETE")))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("ACCOUNT_DELETION_LAST_SUPER_ADMIN");
        assertThat(admin.getIsActive()).isTrue();
        assertThat(admin.getDeletionRequestedAt()).isNull();
        verify(authTokenRepository, org.mockito.Mockito.never()).deleteByUserId(any());
    }

    @Test
    void organizerOfAnActiveRaceWithNoOtherManagerCannotScheduleDeletion() {
        User organizer = user();
        organizer.setRole(User.UserRole.RACE_ADMIN);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(organizer));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(raceRepository.countActiveWithoutOtherManager(org.mockito.ArgumentMatchers.eq(userId), any())).thenReturn(1L);

        assertThatThrownBy(() -> service.request(userId, request("pw", "DELETE")))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("ACCOUNT_DELETION_ACTIVE_RACES");
        assertThat(organizer.getDeletionRequestedAt()).isNull();
    }

    @Test
    void statusReportsBlockersBeforeTheUserConfirms() {
        User admin = user();
        admin.setRole(User.UserRole.SUPER_ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));
        when(userRepository.countOtherActiveByRole(User.UserRole.SUPER_ADMIN, userId)).thenReturn(0L);
        when(raceRepository.countActiveWithoutOtherManager(org.mockito.ArgumentMatchers.eq(userId), any())).thenReturn(2L);

        DeletionStatusDto status = service.status(userId);

        assertThat(status.isDeletionRequested()).isFalse();
        assertThat(status.getBlockers())
            .containsExactly("ACCOUNT_DELETION_LAST_SUPER_ADMIN", "ACCOUNT_DELETION_ACTIVE_RACES");
    }

    @Test
    void deletionRequestsAreSerializedBeforeBlockersAreEvaluated() {
        User admin = user();
        admin.setRole(User.UserRole.SUPER_ADMIN);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(userRepository.countOtherActiveByRole(User.UserRole.SUPER_ADMIN, userId)).thenReturn(1L);

        service.request(userId, request("pw", "DELETE"));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(userRepository);
        order.verify(userRepository).acquireTransactionLock(org.mockito.ArgumentMatchers.anyLong());
        order.verify(userRepository).findByIdForUpdate(userId);
        order.verify(userRepository).countOtherActiveByRole(User.UserRole.SUPER_ADMIN, userId);
    }

    @Test
    void superAdminWithAnotherActiveSuperAdminCanScheduleDeletion() {
        User admin = user();
        admin.setRole(User.UserRole.SUPER_ADMIN);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(userRepository.countOtherActiveByRole(User.UserRole.SUPER_ADMIN, userId)).thenReturn(1L);

        assertThat(service.request(userId, request("pw", "DELETE")).isDeletionRequested()).isTrue();
    }

    @Test
    void requestRequiresConfirmationPhrase() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.request(userId, request("pw", "yes")))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("ACCOUNT_DELETION_CONFIRMATION_REQUIRED");
    }

    @Test
    void cancelReactivatesAccount() {
        User user = user();
        user.setDeletionRequestedAt(OffsetDateTime.now());
        user.setDeletionScheduledFor(OffsetDateTime.now().plusDays(30));
        user.setIsActive(false);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        DeletionStatusDto status = service.cancel(userId);

        assertThat(status.isDeletionRequested()).isFalse();
        assertThat(user.getIsActive()).isTrue();
        assertThat(user.getDeletionScheduledFor()).isNull();
    }

    @Test
    void purgeExpiredStagesDueAccountsWithoutSendingCompletion() {
        User due = user();
        due.setId(UUID.randomUUID());
        due.setDeletionScheduledFor(OffsetDateTime.now().minusMinutes(1));
        when(userRepository.findDueForPurge(any(OffsetDateTime.class), any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(List.of(due));

        int purged = service.purgeExpired();

        assertThat(purged).isEqualTo(1);
        verify(accountPrivacyService).eraseAccount(due.getId());
        org.mockito.Mockito.verify(authEmailService, org.mockito.Mockito.never())
            .sendAccountDeletionCompletedConfirmed(any(), any(), any());
    }

    @Test
    void failedErasureDoesNotStarveLaterDueAccounts() {
        User failed = user();
        failed.setId(UUID.randomUUID());
        failed.setDeletionScheduledFor(OffsetDateTime.now().minusMinutes(3));
        User next = user();
        next.setId(UUID.randomUUID());
        next.setDeletionScheduledFor(OffsetDateTime.now().minusMinutes(2));
        User later = user();
        later.setId(UUID.randomUUID());
        later.setDeletionScheduledFor(OffsetDateTime.now().minusMinutes(1));
        when(userRepository.findDueForPurge(any(OffsetDateTime.class), any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(List.of(failed, next));
        when(userRepository.findDueForPurgeAfter(any(OffsetDateTime.class), any(OffsetDateTime.class), any(UUID.class),
            org.mockito.ArgumentMatchers.eq(50))).thenReturn(List.of(later), List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("storage unavailable"))
            .when(accountPrivacyService).eraseAccount(failed.getId());

        assertThat(service.purgeExpired()).isEqualTo(2);

        verify(accountPrivacyService).eraseAccount(next.getId());
        verify(accountPrivacyService).eraseAccount(later.getId());
    }

    @Test
    void passwordRecoveryReactivatesOnlyWithinGracePeriod() {
        User user = user();
        user.setIsActive(false);
        user.setDeletionRequestedAt(OffsetDateTime.now().minusDays(1));
        user.setDeletionScheduledFor(OffsetDateTime.now().plusDays(1));
        when(userRepository.findByEmailIgnoreCaseForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        service.cancelWithPassword(user.getEmail(), "pw");

        assertThat(user.getIsActive()).isTrue();
        assertThat(user.getDeletionRequestedAt()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void passwordRecoveryDoesNotReactivateAfterDeadline() {
        User user = user();
        user.setIsActive(false);
        user.setDeletionRequestedAt(OffsetDateTime.now().minusDays(31));
        user.setDeletionScheduledFor(OffsetDateTime.now().minusHours(1));
        when(userRepository.findByEmailIgnoreCaseForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        service.cancelWithPassword(user.getEmail(), "pw");

        assertThat(user.getIsActive()).isFalse();
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(user);
    }

    @Test
    void passwordRecoveryWithWrongCredentialsDoesNotReactivate() {
        User user = user();
        user.setIsActive(false);
        user.setDeletionRequestedAt(OffsetDateTime.now());
        user.setDeletionScheduledFor(OffsetDateTime.now().plusDays(1));
        when(userRepository.findByEmailIgnoreCaseForUpdate(user.getEmail())).thenReturn(Optional.of(user));

        service.cancelWithPassword(user.getEmail(), "wrong");

        assertThat(user.getIsActive()).isFalse();
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(user);
    }
}
