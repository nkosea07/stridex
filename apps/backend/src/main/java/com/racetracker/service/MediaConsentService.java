package com.racetracker.service;

import com.racetracker.dto.media.MediaConsentDto;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class MediaConsentService {
    public static final String CURRENT_MEDIA_PUBLICATION_CONSENT_VERSION = "MEDIA_PUBLICATION_V1";
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final AuditEventService auditEventService;

    public MediaConsentService(ParticipantRepository participantRepository, UserRepository userRepository,
                               AuditEventService auditEventService) {
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.auditEventService = auditEventService;
    }

    @Transactional
    public MediaConsentDto update(UUID participantId, String actorEmail, boolean consented) {
        UUID userId = userRepository.findByEmailIgnoreCase(actorEmail)
            .orElseThrow(() -> new ResourceNotFoundException("Registration not found")).getId();
        Participant participant = participantRepository.findByIdForUpdate(participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));
        if (participant.getUser() == null || !userId.equals(participant.getUser().getId())) {
            throw new ResourceNotFoundException("Registration not found");
        }
        participant.setMediaPublicationConsentAt(consented ? OffsetDateTime.now() : null);
        participant.setMediaPublicationConsentVersion(consented ? CURRENT_MEDIA_PUBLICATION_CONSENT_VERSION : null);
        auditEventService.recordInCurrentTransaction("MEDIA_PUBLICATION_CONSENT_CHANGED", userId,
            "PARTICIPANT", participantId.toString(), "SUCCESS", Map.of("consented", consented));
        return new MediaConsentDto(participantId, consented, participant.getMediaPublicationConsentVersion(),
            participant.getMediaPublicationConsentAt());
    }
}
