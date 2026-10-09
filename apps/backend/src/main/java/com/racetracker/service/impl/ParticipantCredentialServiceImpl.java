package com.racetracker.service.impl;

import com.racetracker.dto.participant.ParticipantCredentialRequest;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.ParticipantCredential;
import com.racetracker.repository.ParticipantCredentialRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.service.ParticipantCredentialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ParticipantCredentialServiceImpl implements ParticipantCredentialService {

    private final ParticipantCredentialRepository participantCredentialRepository;
    private final ParticipantRepository participantRepository;

    public ParticipantCredentialServiceImpl(
        ParticipantCredentialRepository participantCredentialRepository,
        ParticipantRepository participantRepository
    ) {
        this.participantCredentialRepository = participantCredentialRepository;
        this.participantRepository = participantRepository;
    }

    @Override
    @Transactional
    public void syncParticipantCredentials(
        Participant participant,
        String legacyRfidTag,
        List<ParticipantCredentialRequest> requestedCredentials
    ) {
        if (participant.getId() == null) {
            throw new IllegalArgumentException("Participant must be persisted before syncing credentials");
        }

        if (requestedCredentials != null) {
            for (ParticipantCredentialRequest request : requestedCredentials) {
                upsertCredential(participant, parseType(request.getType()), request.getValue());
            }
        }

        if (legacyRfidTag != null) {
            upsertCredential(participant, ParticipantCredential.CredentialType.RFID_UHF_TIMING, legacyRfidTag);
            participant.setRfidTag(normalizeValue(legacyRfidTag));
            participantRepository.save(participant);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Participant resolveTimingParticipant(
        ParticipantCredential.CredentialType credentialType,
        Participant participantFallback,
        UUID raceId,
        String credentialValue
    ) {
        String normalizedValue = normalizeValue(credentialValue);
        if (normalizedValue == null) {
            throw new ResourceNotFoundException("Credential value is required");
        }

        return participantCredentialRepository.findByRaceIdAndCredentialTypeAndCredentialValueAndStatus(
                raceId,
                credentialType,
                normalizedValue,
                ParticipantCredential.CredentialStatus.ACTIVE
            )
            .map(ParticipantCredential::getParticipant)
            .or(() -> participantFallback == null
                ? participantRepository.findByRaceIdAndRfidTag(raceId, normalizedValue)
                : java.util.Optional.of(participantFallback))
            .orElse(null);
    }

    private void upsertCredential(
        Participant participant,
        ParticipantCredential.CredentialType type,
        String rawValue
    ) {
        String normalizedValue = normalizeValue(rawValue);
        List<ParticipantCredential> existing = participantCredentialRepository.findByParticipantIdAndCredentialTypeOrderByAssignedAtAsc(
            participant.getId(),
            type
        );

        if (normalizedValue == null) {
            revokeActive(existing);
            if (type == ParticipantCredential.CredentialType.RFID_UHF_TIMING) {
                participant.setRfidTag(null);
                participantRepository.save(participant);
            }
            return;
        }

        if (participantCredentialRepository.existsByRaceIdAndCredentialTypeAndCredentialValueAndParticipantIdNot(
            participant.getRace().getId(),
            type,
            normalizedValue,
            participant.getId()
        )) {
            throw new ConflictException(type.name() + " credential already belongs to another participant");
        }

        for (ParticipantCredential credential : existing) {
            if (credential.getStatus() == ParticipantCredential.CredentialStatus.ACTIVE
                && normalizedValue.equals(credential.getCredentialValue())) {
                return;
            }
        }

        revokeActive(existing);

        ParticipantCredential credential = ParticipantCredential.builder()
            .participant(participant)
            .race(participant.getRace())
            .credentialType(type)
            .credentialValue(normalizedValue)
            .status(ParticipantCredential.CredentialStatus.ACTIVE)
            .assignedAt(OffsetDateTime.now())
            .build();
        participantCredentialRepository.save(credential);
        participant.getCredentials().add(credential);
        if (type == ParticipantCredential.CredentialType.RFID_UHF_TIMING) {
            participant.setRfidTag(normalizedValue);
            participantRepository.save(participant);
        }
    }

    private void revokeActive(List<ParticipantCredential> credentials) {
        OffsetDateTime revokedAt = OffsetDateTime.now();
        for (ParticipantCredential credential : credentials) {
            if (credential.getStatus() == ParticipantCredential.CredentialStatus.ACTIVE) {
                credential.setStatus(ParticipantCredential.CredentialStatus.REVOKED);
                credential.setRevokedAt(revokedAt);
                participantCredentialRepository.save(credential);
            }
        }
    }

    private ParticipantCredential.CredentialType parseType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new ResourceNotFoundException("Credential type is required");
        }
        try {
            return ParticipantCredential.CredentialType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException("Unsupported credential type: " + rawType);
        }
    }

    private String normalizeValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
