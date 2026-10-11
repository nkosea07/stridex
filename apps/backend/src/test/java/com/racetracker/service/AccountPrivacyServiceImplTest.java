package com.racetracker.service;

import com.racetracker.dto.privacy.ConsentDto;
import com.racetracker.dto.privacy.ConsentUpdateRequest;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.AuthTokenRepository;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.AccountPrivacyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPrivacyServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock PushDeviceRepository pushDeviceRepository;
    @Mock AuthTokenRepository authTokenRepository;
    @Mock PasswordEncoder passwordEncoder;

    private AccountPrivacyServiceImpl service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AccountPrivacyServiceImpl(userRepository, participantRepository, notificationRepository,
            pushDeviceRepository, authTokenRepository, passwordEncoder);
    }

    private User user() {
        return User.builder().id(userId).email("runner@example.com").firstName("Ada").lastName("Runner")
            .isActive(true).emailVerified(true).marketingConsent(false).build();
    }

    @Test
    void exportIncludesProfileRegistrationsAndConsent() {
        User user = user();
        Race race = new Race();
        race.setId(UUID.randomUUID());
        race.setName("Demo Marathon");
        Participant participant = Participant.builder().id(UUID.randomUUID()).race(race)
            .status(Participant.ParticipantStatus.CONFIRMED).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(participantRepository.findByUserIdOrderByRegisteredAtDesc(userId)).thenReturn(List.of(participant));
        when(notificationRepository.countByUserId(userId)).thenReturn(3L);

        var data = service.exportData(userId);

        assertThat(data).containsKeys("profile", "consent", "registrations", "notificationCount");
        assertThat(data.get("notificationCount")).isEqualTo(3L);
    }

    @Test
    void eraseAnonymizesUserAndParticipants() {
        User user = user();
        Race race = new Race();
        race.setId(UUID.randomUUID());
        Participant participant = Participant.builder().id(UUID.randomUUID()).race(race)
            .firstName("Ada").lastName("Runner").email("runner@example.com").phone("0700")
            .emergencyContactPhone("0711").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(participantRepository.findByUserIdOrderByRegisteredAtDesc(userId)).thenReturn(List.of(participant));
        when(authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(any(), any())).thenReturn(List.of());
        when(passwordEncoder.encode(any())).thenReturn("newhash");

        service.eraseAccount(userId);

        assertThat(user.getEmail()).startsWith("deleted+");
        assertThat(user.getIsActive()).isFalse();
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(participant.getEmail()).startsWith("deleted+");
        assertThat(participant.getFirstName()).isEqualTo("Anonymous");
        assertThat(participant.getPhone()).isNull();
        assertThat(participant.getEmergencyContactPhone()).isNull();
        verify(pushDeviceRepository).deleteByUserId(userId);
    }

    @Test
    void updateConsentRecordsDataProcessingTimestamp() {
        User user = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsentUpdateRequest request = new ConsentUpdateRequest();
        request.setDataProcessingConsent(true);
        request.setMarketingConsent(true);

        ConsentDto dto = service.updateConsent(userId, request);

        assertThat(dto.getDataProcessingConsentAt()).isNotNull();
        assertThat(dto.isMarketingConsent()).isTrue();
    }
}
