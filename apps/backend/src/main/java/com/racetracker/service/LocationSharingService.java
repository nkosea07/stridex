package com.racetracker.service;

import com.racetracker.dto.participant.LocationSharingDto;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class LocationSharingService {
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final AuditEventService auditEventService;

    public LocationSharingService(ParticipantRepository participantRepository, UserRepository userRepository,
                                  AuditEventService auditEventService) {
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.auditEventService = auditEventService;
    }

    @Transactional
    public LocationSharingDto update(UUID participantId, String actorEmail, boolean enabled) {
        UUID userId = userRepository.findByEmailIgnoreCase(actorEmail)
            .orElseThrow(() -> new ResourceNotFoundException("Registration not found")).getId();
        Participant participant = participantRepository.findByIdForUpdate(participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));
        if (participant.getUser() == null || !userId.equals(participant.getUser().getId())) {
            throw new ResourceNotFoundException("Registration not found");
        }
        participant.setPublicLocationSharing(enabled);
        auditEventService.recordInCurrentTransaction("PUBLIC_LOCATION_SHARING_CHANGED", userId,
            "PARTICIPANT", participantId.toString(), "SUCCESS", Map.of("enabled", enabled));
        return new LocationSharingDto(participantId, enabled);
    }
}
