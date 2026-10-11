package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.privacy.DeleteAccountRequest;
import com.racetracker.dto.privacy.DeletionStatusDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.User;
import com.racetracker.repository.AuthTokenRepository;
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

    @Mock UserRepository userRepository;
    @Mock AuthTokenRepository authTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditEventService auditEventService;
    @Mock AccountPrivacyService accountPrivacyService;

    private AccountDeletionServiceImpl service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getPrivacy().setDeletionGraceDays(30);
        service = new AccountDeletionServiceImpl(userRepository, authTokenRepository, passwordEncoder,
            auditEventService, accountPrivacyService, properties);
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
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        DeletionStatusDto status = service.request(userId, request("pw", "DELETE"));

        assertThat(status.isDeletionRequested()).isTrue();
        assertThat(status.getScheduledFor()).isAfter(OffsetDateTime.now().plusDays(29));
        assertThat(user.getIsActive()).isFalse();
        verify(userRepository).save(user);
        verify(authTokenRepository).deleteByUserId(userId);
    }

    @Test
    void requestRejectsWrongPassword() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("nope", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.request(userId, request("nope", "DELETE")))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void requestRequiresConfirmationPhrase() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.request(userId, request("pw", "yes")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("DELETE");
    }

    @Test
    void cancelReactivatesAccount() {
        User user = user();
        user.setDeletionRequestedAt(OffsetDateTime.now());
        user.setDeletionScheduledFor(OffsetDateTime.now().plusDays(30));
        user.setIsActive(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        DeletionStatusDto status = service.cancel(userId);

        assertThat(status.isDeletionRequested()).isFalse();
        assertThat(user.getIsActive()).isTrue();
        assertThat(user.getDeletionScheduledFor()).isNull();
    }

    @Test
    void purgeExpiredErasesDueAccounts() {
        User due = user();
        due.setId(UUID.randomUUID());
        when(userRepository.findDueForPurge(any(OffsetDateTime.class))).thenReturn(List.of(due));

        int purged = service.purgeExpired();

        assertThat(purged).isEqualTo(1);
        verify(accountPrivacyService).eraseAccount(due.getId());
    }
}
