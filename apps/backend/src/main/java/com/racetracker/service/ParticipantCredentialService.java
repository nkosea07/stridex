package com.racetracker.service;

import com.racetracker.dto.participant.ParticipantCredentialRequest;
import com.racetracker.model.Participant;
import com.racetracker.model.ParticipantCredential;

import java.util.List;

public interface ParticipantCredentialService {
    void syncParticipantCredentials(Participant participant, String legacyRfidTag, List<ParticipantCredentialRequest> requestedCredentials);
    Participant resolveTimingParticipant(ParticipantCredential.CredentialType credentialType, Participant participantFallback, java.util.UUID raceId, String credentialValue);
}
