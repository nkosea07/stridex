package com.racetracker.service.impl;

import com.racetracker.dto.privacy.ConsentDto;
import com.racetracker.dto.privacy.ConsentUpdateRequest;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.AccountErasure;
import com.racetracker.model.Participant;
import com.racetracker.model.PrivacyObjectDeletionJob;
import com.racetracker.model.User;
import com.racetracker.repository.AccountErasureRepository;
import com.racetracker.repository.AthleteFollowRepository;
import com.racetracker.repository.AuthTokenRepository;
import com.racetracker.repository.EmergencyAlertRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.NotificationPreferenceRepository;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.ParticipantCredentialRepository;
import com.racetracker.repository.ParticipantRegistrationAnswerRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.repository.PrivacyObjectDeletionJobRepository;
import com.racetracker.repository.RaceMediaRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.AccountPrivacyService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AccountPrivacyServiceImpl implements AccountPrivacyService {

    private final UserRepository userRepository;
    private final ParticipantRepository participantRepository;
    private final NotificationRepository notificationRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final AuthTokenRepository authTokenRepository;
    private final ParticipantCredentialRepository participantCredentialRepository;
    private final ParticipantRegistrationAnswerRepository registrationAnswerRepository;
    private final GpsLocationRepository gpsLocationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final AthleteFollowRepository athleteFollowRepository;
    private final RaceMediaRepository raceMediaRepository;
    private final AccountErasureRepository accountErasureRepository;
    private final PrivacyObjectDeletionJobRepository deletionJobRepository;
    private final EmergencyAlertRepository emergencyAlertRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.racetracker.service.AuditEventService auditEventService;
    private final TransactionTemplate isolatedTransaction;

    public AccountPrivacyServiceImpl(
        UserRepository userRepository,
        ParticipantRepository participantRepository,
        NotificationRepository notificationRepository,
        PushDeviceRepository pushDeviceRepository,
        AuthTokenRepository authTokenRepository,
        ParticipantCredentialRepository participantCredentialRepository,
        ParticipantRegistrationAnswerRepository registrationAnswerRepository,
        GpsLocationRepository gpsLocationRepository,
        NotificationPreferenceRepository notificationPreferenceRepository,
        AthleteFollowRepository athleteFollowRepository,
        RaceMediaRepository raceMediaRepository,
        AccountErasureRepository accountErasureRepository,
        PrivacyObjectDeletionJobRepository deletionJobRepository,
        EmergencyAlertRepository emergencyAlertRepository,
        PasswordEncoder passwordEncoder,
        com.racetracker.service.AuditEventService auditEventService,
        PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.participantRepository = participantRepository;
        this.notificationRepository = notificationRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.authTokenRepository = authTokenRepository;
        this.participantCredentialRepository = participantCredentialRepository;
        this.registrationAnswerRepository = registrationAnswerRepository;
        this.gpsLocationRepository = gpsLocationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.athleteFollowRepository = athleteFollowRepository;
        this.raceMediaRepository = raceMediaRepository;
        this.accountErasureRepository = accountErasureRepository;
        this.deletionJobRepository = deletionJobRepository;
        this.emergencyAlertRepository = emergencyAlertRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditEventService = auditEventService;
        this.isolatedTransaction = new TransactionTemplate(transactionManager);
        this.isolatedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public Map<String, Object> exportData(UUID userId) {
        User user = requireUser(userId);
        auditEventService.recordInCurrentTransaction("ACCOUNT_DATA_EXPORTED", userId, "USER", userId.toString(),
            "SUCCESS", Map.of());
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("email", user.getEmail());
        profile.put("firstName", user.getFirstName());
        profile.put("lastName", user.getLastName());
        profile.put("phone", user.getPhone());
        profile.put("role", user.getRole() != null ? user.getRole().name() : null);
        profile.put("emailVerified", user.getEmailVerified());
        profile.put("createdAt", user.getCreatedAt());

        List<Map<String, Object>> registrations = new ArrayList<>();
        for (Participant participant : participantRepository.findByUserIdOrderByRegisteredAtDesc(userId)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("raceId", participant.getRace() != null ? participant.getRace().getId() : null);
            entry.put("raceName", participant.getRace() != null ? participant.getRace().getName() : null);
            entry.put("bibNumber", participant.getBibNumber());
            entry.put("status", participant.getStatus() != null ? participant.getStatus().name() : null);
            entry.put("registeredAt", participant.getRegisteredAt());
            registrations.add(entry);
        }

        Map<String, Object> consent = new LinkedHashMap<>();
        consent.put("dataProcessingConsentAt", user.getDataProcessingConsentAt());
        consent.put("marketingConsent", Boolean.TRUE.equals(user.getMarketingConsent()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", OffsetDateTime.now());
        result.put("profile", profile);
        result.put("consent", consent);
        result.put("registrations", registrations);
        result.put("notificationCount", notificationRepository.countByUserId(userId));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ConsentDto getConsent(UUID userId) {
        User user = requireUser(userId);
        ConsentDto dto = new ConsentDto();
        dto.setDataProcessingConsentAt(user.getDataProcessingConsentAt());
        dto.setMarketingConsent(Boolean.TRUE.equals(user.getMarketingConsent()));
        return dto;
    }

    @Override
    @Transactional
    public ConsentDto updateConsent(UUID userId, ConsentUpdateRequest request) {
        User user = requireUser(userId);
        if (Boolean.TRUE.equals(request.getDataProcessingConsent())) {
            if (user.getDataProcessingConsentAt() == null) {
                user.setDataProcessingConsentAt(OffsetDateTime.now());
            }
        } else if (Boolean.FALSE.equals(request.getDataProcessingConsent())) {
            user.setDataProcessingConsentAt(null);
        }
        if (request.getMarketingConsent() != null) {
            user.setMarketingConsent(request.getMarketingConsent());
        }
        User saved = userRepository.save(user);
        ConsentDto dto = new ConsentDto();
        dto.setDataProcessingConsentAt(saved.getDataProcessingConsentAt());
        dto.setMarketingConsent(Boolean.TRUE.equals(saved.getMarketingConsent()));
        return dto;
    }

    @Override
    @Transactional
    public void eraseAccount(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getDeletedAt() != null || accountErasureRepository.existsById(userId)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (user.getDeletionScheduledFor() == null || user.getDeletionScheduledFor().isAfter(now)) {
            throw new ConflictException("Account deletion is not due");
        }

        AccountErasure erasure = new AccountErasure();
        erasure.setUserId(userId);
        erasure.setStagedAt(now);
        erasure.setNotificationEmail(user.getEmail());
        erasure.setNotificationFirstName(user.getFirstName());
        accountErasureRepository.saveAndFlush(erasure);
        deletionJobRepository.stageOriginals(userId, now);
        deletionJobRepository.stageThumbnails(userId, now);
        raceMediaRepository.deletePersonalMediaForUser(userId);
        auditEventService.recordInCurrentTransaction("ACCOUNT_ERASURE_STAGED", userId, "USER", userId.toString(),
            "SUCCESS", Map.of());
    }

    public record ClaimedJob(UUID id, String storageKey, String storageScope, UUID leaseToken) {}

    @Transactional
    public List<ClaimedJob> claimJobs(int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        List<ClaimedJob> claimed = new ArrayList<>();
        for (UUID id : deletionJobRepository.findClaimableIds(now, limit)) {
            PrivacyObjectDeletionJob job = deletionJobRepository.findById(id).orElseThrow();
            UUID token = UUID.randomUUID();
            job.setStatus(PrivacyObjectDeletionJob.Status.PROCESSING);
            job.setAttempts(job.getAttempts() + 1);
            job.setLeaseToken(token);
            job.setLeaseUntil(now.plusMinutes(5));
            claimed.add(new ClaimedJob(id, job.getStorageKey(), job.getStorageScope(), token));
        }
        return claimed;
    }

    @Transactional
    public void completeJob(UUID id, UUID leaseToken) {
        PrivacyObjectDeletionJob job = deletionJobRepository.findByIdForUpdate(id).orElseThrow();
        if (job.getStatus() != PrivacyObjectDeletionJob.Status.PROCESSING
            || !leaseToken.equals(job.getLeaseToken())) {
            return;
        }
        job.setStatus(PrivacyObjectDeletionJob.Status.COMPLETED);
        job.setCompletedAt(OffsetDateTime.now());
        job.setLeaseToken(null);
        job.setLeaseUntil(null);
    }

    @Transactional
    public void retryJob(UUID id, UUID leaseToken) {
        PrivacyObjectDeletionJob job = deletionJobRepository.findByIdForUpdate(id).orElseThrow();
        if (job.getStatus() != PrivacyObjectDeletionJob.Status.PROCESSING
            || !leaseToken.equals(job.getLeaseToken())) {
            return;
        }
        job.setStatus(PrivacyObjectDeletionJob.Status.PENDING);
        job.setLeaseToken(null);
        job.setLeaseUntil(null);
        long delaySeconds = Math.min(3600, 5L << Math.min(job.getAttempts(), 10));
        job.setNextAttemptAt(OffsetDateTime.now().plusSeconds(delaySeconds));
    }

    public int finalizeReady(int limit) {
        int finalized = 0;
        for (UUID userId : accountErasureRepository.findReadyToFinalize(
            PrivacyObjectDeletionJob.Status.COMPLETED, OffsetDateTime.now(), PageRequest.of(0, limit))) {
            try {
                Boolean done = isolatedTransaction.execute(status -> finalizeOne(userId));
                if (Boolean.TRUE.equals(done)) finalized++;
            } catch (RuntimeException ex) {
                log.error("Account erasure finalization failed; userId={}, errorType={}",
                    userId, ex.getClass().getSimpleName());
                recordFinalizationFailure(userId);
            }
        }
        return finalized;
    }

    public int repairLegacyAccounts(int limit) {
        int repaired = 0;
        for (UUID userId : accountErasureRepository.findLegacyScrubDue(
            OffsetDateTime.now(), PageRequest.of(0, limit))) {
            try {
                isolatedTransaction.executeWithoutResult(status -> {
                    AccountErasure erasure = accountErasureRepository.findByUserIdForUpdate(userId).orElseThrow();
                    if (erasure.getLegacyScrubbedAt() != null) return;
                    scrubParticipants(userId);
                    erasure.setLegacyScrubbedAt(OffsetDateTime.now());
                    erasure.setFinalizationErrorCode(null);
                    erasure.setFinalizationNextAttemptAt(null);
                });
                repaired++;
            } catch (RuntimeException ex) {
                log.error("Legacy account scrub failed; userId={}, errorType={}",
                    userId, ex.getClass().getSimpleName());
                recordFinalizationFailure(userId);
            }
        }
        return repaired;
    }

    private boolean finalizeOne(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow();
        AccountErasure erasure = accountErasureRepository.findByUserIdForUpdate(userId).orElseThrow();
        if (erasure.getFinalizedAt() != null || deletionJobRepository.countByUserIdAndStatusNot(
            userId, PrivacyObjectDeletionJob.Status.COMPLETED) != 0) return false;
        anonymizeAccount(user);
        OffsetDateTime now = OffsetDateTime.now();
        erasure.setFinalizedAt(now);
        erasure.setLegacyScrubbedAt(now);
        erasure.setFinalizationErrorCode(null);
        erasure.setFinalizationNextAttemptAt(null);
        return true;
    }

    private void recordFinalizationFailure(UUID userId) {
        try {
            isolatedTransaction.executeWithoutResult(status -> {
                AccountErasure erasure = accountErasureRepository.findByUserIdForUpdate(userId).orElseThrow();
                int attempts = erasure.getFinalizationAttempts() == null ? 1 : erasure.getFinalizationAttempts() + 1;
                erasure.setFinalizationAttempts(attempts);
                erasure.setFinalizationErrorCode("FINALIZATION_FAILED");
                erasure.setFinalizationNextAttemptAt(OffsetDateTime.now().plusSeconds(
                    Math.min(3600, 5L << Math.min(attempts, 10))));
            });
        } catch (RuntimeException ex) {
            log.error("Could not persist erasure retry state; userId={}, errorType={}",
                userId, ex.getClass().getSimpleName());
        }
    }

    public record NotificationClaim(UUID userId, String email, String firstName, UUID leaseToken) {}

    @Transactional
    public List<NotificationClaim> claimNotifications(int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        List<NotificationClaim> claimed = new ArrayList<>();
        for (UUID userId : accountErasureRepository.findNotificationClaims(now, limit)) {
            AccountErasure erasure = accountErasureRepository.findById(userId).orElseThrow();
            UUID token = UUID.randomUUID();
            erasure.setNotificationLeaseToken(token);
            erasure.setNotificationLeaseUntil(now.plusMinutes(5));
            claimed.add(new NotificationClaim(userId, erasure.getNotificationEmail(),
                erasure.getNotificationFirstName(), token));
        }
        return claimed;
    }

    @Transactional
    public void completeNotification(UUID userId, UUID leaseToken) {
        AccountErasure erasure = accountErasureRepository.findByUserIdForUpdate(userId).orElseThrow();
        if (!leaseToken.equals(erasure.getNotificationLeaseToken())) {
            return;
        }
        erasure.setNotificationDispatchedAt(OffsetDateTime.now());
        erasure.setNotificationEmail(null);
        erasure.setNotificationFirstName(null);
        erasure.setNotificationLeaseToken(null);
        erasure.setNotificationLeaseUntil(null);
    }

    @Transactional
    public void retryNotification(UUID userId, UUID leaseToken) {
        AccountErasure erasure = accountErasureRepository.findByUserIdForUpdate(userId).orElseThrow();
        if (leaseToken.equals(erasure.getNotificationLeaseToken())) {
            erasure.setNotificationLeaseToken(null);
            erasure.setNotificationLeaseUntil(OffsetDateTime.now().plusMinutes(5));
        }
    }

    private void anonymizeAccount(User user) {
        UUID userId = user.getId();

        scrubParticipants(userId);

        // Personal interaction and device data is removed outright.
        pushDeviceRepository.deleteByUserId(userId);
        athleteFollowRepository.deleteByUserId(userId);
        notificationRepository.deleteByUserId(userId);
        notificationPreferenceRepository.deleteByUserId(userId);
        authTokenRepository.deleteByUserId(userId);
        // Safety records are retained but de-identified.
        emergencyAlertRepository.anonymizeReporterByUserId(userId);

        OffsetDateTime now = OffsetDateTime.now();
        String anonymizedEmail = "deleted+" + userId + "@deleted.local";
        user.setEmail(anonymizedEmail);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setPhone(null);
        user.setIsActive(false);
        user.setEmailVerified(false);
        user.setMarketingConsent(false);
        user.setDataProcessingConsentAt(null);
        user.setPrivacyPolicyVersion(null);
        user.setPrivacyPolicyAcceptedAt(null);
        user.setDeletedAt(now);
        userRepository.save(user);

        auditEventService.recordInCurrentTransaction("ACCOUNT_ERASED", userId, "USER", userId.toString(),
            "SUCCESS", Map.of());
    }

    private void scrubParticipants(UUID userId) {
        List<Participant> participants = participantRepository.findByUserIdOrderByRegisteredAtDesc(userId);
        List<UUID> participantIds = participants.stream().map(Participant::getId).toList();
        for (Participant participant : participants) {
            anonymize(participant);
        }
        if (!participants.isEmpty()) {
            participantRepository.saveAll(participants);
            registrationAnswerRepository.deleteByParticipantIdIn(participantIds);
            participantCredentialRepository.deleteByParticipantIdIn(participantIds);
            gpsLocationRepository.deleteByParticipantIdIn(participantIds);
        }
    }

    private void anonymize(Participant participant) {
        String anonymizedEmail = "deleted+" + participant.getId() + "@deleted.local";
        participant.setFirstName("Anonymous");
        participant.setLastName("Runner");
        participant.setEmail(anonymizedEmail);
        participant.setPhone(null);
        participant.setDateOfBirth(null);
        participant.setGender(null);
        participant.setNationality(null);
        participant.setBibNumber(null);
        participant.setRfidTag(null);
        participant.setClubName(null);
        participant.setTeamName(null);
        participant.setTeam(null);
        participant.setShirtSize(null);
        participant.setEmergencyContactName(null);
        participant.setEmergencyContactPhone(null);
        participant.setEmergencyContactRelation(null);
        participant.setMedicalConditions(null);
        participant.setAllergies(null);
        participant.setBloodType(null);
        participant.setMediaPublicationConsentAt(null);
        participant.setMediaPublicationConsentVersion(null);
        participant.setDeviceToken(null);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
