package com.racetracker.service.impl;

import com.racetracker.dto.privacy.ConsentDto;
import com.racetracker.dto.privacy.ConsentUpdateRequest;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.User;
import com.racetracker.repository.AthleteFollowRepository;
import com.racetracker.repository.AuthTokenRepository;
import com.racetracker.repository.EmergencyAlertRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.NotificationPreferenceRepository;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.ParticipantCredentialRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.repository.RaceMediaRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.AccountPrivacyService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountPrivacyServiceImpl implements AccountPrivacyService {

    private final UserRepository userRepository;
    private final ParticipantRepository participantRepository;
    private final NotificationRepository notificationRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final AuthTokenRepository authTokenRepository;
    private final ParticipantCredentialRepository participantCredentialRepository;
    private final GpsLocationRepository gpsLocationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final AthleteFollowRepository athleteFollowRepository;
    private final RaceMediaRepository raceMediaRepository;
    private final EmergencyAlertRepository emergencyAlertRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.racetracker.service.AuditEventService auditEventService;

    public AccountPrivacyServiceImpl(
        UserRepository userRepository,
        ParticipantRepository participantRepository,
        NotificationRepository notificationRepository,
        PushDeviceRepository pushDeviceRepository,
        AuthTokenRepository authTokenRepository,
        ParticipantCredentialRepository participantCredentialRepository,
        GpsLocationRepository gpsLocationRepository,
        NotificationPreferenceRepository notificationPreferenceRepository,
        AthleteFollowRepository athleteFollowRepository,
        RaceMediaRepository raceMediaRepository,
        EmergencyAlertRepository emergencyAlertRepository,
        PasswordEncoder passwordEncoder,
        com.racetracker.service.AuditEventService auditEventService
    ) {
        this.userRepository = userRepository;
        this.participantRepository = participantRepository;
        this.notificationRepository = notificationRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.authTokenRepository = authTokenRepository;
        this.participantCredentialRepository = participantCredentialRepository;
        this.gpsLocationRepository = gpsLocationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.athleteFollowRepository = athleteFollowRepository;
        this.raceMediaRepository = raceMediaRepository;
        this.emergencyAlertRepository = emergencyAlertRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional(readOnly = true)
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
        User user = requireUser(userId);

        List<Participant> participants = participantRepository.findByUserIdOrderByRegisteredAtDesc(userId);
        List<UUID> participantIds = participants.stream().map(Participant::getId).toList();
        for (Participant participant : participants) {
            anonymize(participant);
        }
        if (!participants.isEmpty()) {
            participantRepository.saveAll(participants);
            participantCredentialRepository.deleteByParticipantIdIn(participantIds);
            gpsLocationRepository.deleteByParticipantIdIn(participantIds);
        }

        // Personal interaction and device data is removed outright.
        pushDeviceRepository.deleteByUserId(userId);
        athleteFollowRepository.deleteByUserId(userId);
        notificationRepository.deleteByUserId(userId);
        notificationPreferenceRepository.deleteByUserId(userId);
        authTokenRepository.deleteByUserId(userId);
        raceMediaRepository.deleteByUploadedBy(userId);
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

    private void anonymize(Participant participant) {
        String anonymizedEmail = "deleted+" + participant.getId() + "@deleted.local";
        participant.setFirstName("Anonymous");
        participant.setLastName("Runner");
        participant.setEmail(anonymizedEmail);
        participant.setPhone(null);
        participant.setEmergencyContactName(null);
        participant.setEmergencyContactPhone(null);
        participant.setEmergencyContactRelation(null);
        participant.setMedicalConditions(null);
        participant.setAllergies(null);
        participant.setDeviceToken(null);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
