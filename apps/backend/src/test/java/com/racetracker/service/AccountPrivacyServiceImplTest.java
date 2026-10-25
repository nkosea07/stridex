package com.racetracker.service;

import com.racetracker.dto.privacy.ConsentDto;
import com.racetracker.dto.privacy.ConsentUpdateRequest;
import com.racetracker.model.Participant;
import com.racetracker.model.AccountErasure;
import com.racetracker.model.PrivacyObjectDeletionJob;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.AthleteFollowRepository;
import com.racetracker.repository.AccountErasureRepository;
import com.racetracker.repository.AuthTokenRepository;
import com.racetracker.repository.EmergencyAlertRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.NotificationPreferenceRepository;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.ParticipantCredentialRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.repository.PrivacyObjectDeletionJobRepository;
import com.racetracker.repository.RaceMediaRepository;
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
import java.time.OffsetDateTime;

import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class AccountPrivacyServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock PushDeviceRepository pushDeviceRepository;
    @Mock AuthTokenRepository authTokenRepository;
    @Mock ParticipantCredentialRepository participantCredentialRepository;
    @Mock GpsLocationRepository gpsLocationRepository;
    @Mock NotificationPreferenceRepository notificationPreferenceRepository;
    @Mock AthleteFollowRepository athleteFollowRepository;
    @Mock RaceMediaRepository raceMediaRepository;
    @Mock AccountErasureRepository accountErasureRepository;
    @Mock PrivacyObjectDeletionJobRepository deletionJobRepository;
    @Mock EmergencyAlertRepository emergencyAlertRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditEventService auditEventService;

    private AccountPrivacyServiceImpl service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AccountPrivacyServiceImpl(userRepository, participantRepository, notificationRepository,
            pushDeviceRepository, authTokenRepository, participantCredentialRepository, gpsLocationRepository,
            notificationPreferenceRepository, athleteFollowRepository, raceMediaRepository, accountErasureRepository,
            deletionJobRepository, emergencyAlertRepository,
            passwordEncoder, auditEventService);
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
    void erasureStagesObjectsAndRemovesMediaMetadataBeforeAnonymization() {
        User user = user();
        user.setDeletionScheduledFor(OffsetDateTime.now().minusMinutes(1));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(accountErasureRepository.saveAndFlush(any(AccountErasure.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.eraseAccount(userId);

        var order = inOrder(accountErasureRepository, deletionJobRepository, raceMediaRepository);
        order.verify(accountErasureRepository).saveAndFlush(any(AccountErasure.class));
        order.verify(deletionJobRepository).stageOriginals(any(), any());
        order.verify(deletionJobRepository).stageThumbnails(any(), any());
        order.verify(raceMediaRepository).deletePersonalMediaForUser(userId);
        assertThat(user.getDeletedAt()).isNull();
        assertThat(user.getEmail()).isEqualTo("runner@example.com");
        verify(userRepository, never()).save(user);
    }

    @Test
    void erasureDoesNotRestageExistingBatch() {
        User user = user();
        user.setDeletionScheduledFor(OffsetDateTime.now().minusMinutes(1));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(accountErasureRepository.existsById(userId)).thenReturn(true);

        service.eraseAccount(userId);

        verify(deletionJobRepository, never()).stageOriginals(any(), any());
        verify(raceMediaRepository, never()).deletePersonalMediaForUser(userId);
    }

    @Test
    void finalizationWaitsForEveryObjectJob() {
        AccountErasure erasure = new AccountErasure();
        erasure.setUserId(userId);
        when(accountErasureRepository.findReadyToFinalize(PrivacyObjectDeletionJob.Status.COMPLETED,
            PageRequest.of(0, 50))).thenReturn(List.of(userId));
        when(accountErasureRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(erasure));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user()));
        when(deletionJobRepository.countByUserIdAndStatusNot(userId, PrivacyObjectDeletionJob.Status.COMPLETED))
            .thenReturn(1L);

        assertThat(service.finalizeReady(50)).isZero();

        assertThat(erasure.getFinalizedAt()).isNull();
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void finalizationAnonymizesOnlyAfterAllObjectJobsComplete() {
        User user = user();
        Race race = new Race();
        race.setId(UUID.randomUUID());
        Participant participant = Participant.builder().id(UUID.randomUUID()).race(race)
            .firstName("Ada").lastName("Runner").email("runner@example.com").phone("0700")
            .emergencyContactPhone("0711").build();
        AccountErasure erasure = new AccountErasure();
        erasure.setUserId(userId);
        when(accountErasureRepository.findReadyToFinalize(PrivacyObjectDeletionJob.Status.COMPLETED,
            PageRequest.of(0, 50))).thenReturn(List.of(userId));
        when(accountErasureRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(erasure));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(participantRepository.findByUserIdOrderByRegisteredAtDesc(userId)).thenReturn(List.of(participant));
        when(passwordEncoder.encode(any())).thenReturn("newhash");

        assertThat(service.finalizeReady(50)).isEqualTo(1);

        assertThat(erasure.getFinalizedAt()).isNotNull();
        assertThat(user.getEmail()).startsWith("deleted+");
        assertThat(user.getIsActive()).isFalse();
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(participant.getEmail()).startsWith("deleted+");
        assertThat(participant.getFirstName()).isEqualTo("Anonymous");
        assertThat(participant.getPhone()).isNull();
        assertThat(participant.getEmergencyContactPhone()).isNull();

        verify(pushDeviceRepository).deleteByUserId(userId);
        verify(participantCredentialRepository).deleteByParticipantIdIn(List.of(participant.getId()));
        verify(gpsLocationRepository).deleteByParticipantIdIn(List.of(participant.getId()));
        verify(notificationRepository).deleteByUserId(userId);
        verify(notificationPreferenceRepository).deleteByUserId(userId);
        verify(athleteFollowRepository).deleteByUserId(userId);
        verify(authTokenRepository).deleteByUserId(userId);
        verify(raceMediaRepository, never()).deletePersonalMediaForUser(userId);
        verify(emergencyAlertRepository).anonymizeReporterByUserId(userId);
    }

    @Test
    void storageFailureRequeuesJobAndLeavesItUnfinished() {
        UUID jobId = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();
        PrivacyObjectDeletionJob job = new PrivacyObjectDeletionJob();
        job.setId(jobId);
        job.setStatus(PrivacyObjectDeletionJob.Status.PROCESSING);
        job.setLeaseToken(leaseToken);
        job.setAttempts(1);
        job.setLeaseUntil(OffsetDateTime.now().plusMinutes(5));
        when(deletionJobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));

        service.retryJob(jobId, leaseToken);

        assertThat(job.getStatus()).isEqualTo(PrivacyObjectDeletionJob.Status.PENDING);
        assertThat(job.getNextAttemptAt()).isAfter(OffsetDateTime.now());
        assertThat(job.getCompletedAt()).isNull();
        assertThat(job.getLeaseToken()).isNull();
    }

    @Test
    void staleWorkerCannotMarkAnotherLeaseComplete() {
        UUID jobId = UUID.randomUUID();
        PrivacyObjectDeletionJob job = new PrivacyObjectDeletionJob();
        job.setId(jobId);
        job.setStatus(PrivacyObjectDeletionJob.Status.PROCESSING);
        job.setLeaseToken(UUID.randomUUID());
        when(deletionJobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));

        service.completeJob(jobId, UUID.randomUUID());

        assertThat(job.getStatus()).isEqualTo(PrivacyObjectDeletionJob.Status.PROCESSING);
        assertThat(job.getCompletedAt()).isNull();
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
