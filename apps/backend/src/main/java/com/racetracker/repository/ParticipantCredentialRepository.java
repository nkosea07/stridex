package com.racetracker.repository;

import com.racetracker.model.ParticipantCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantCredentialRepository extends JpaRepository<ParticipantCredential, UUID> {

    Optional<ParticipantCredential> findByRaceIdAndCredentialTypeAndCredentialValueAndStatus(
        UUID raceId,
        ParticipantCredential.CredentialType credentialType,
        String credentialValue,
        ParticipantCredential.CredentialStatus status
    );

    List<ParticipantCredential> findByParticipantIdOrderByAssignedAtAsc(UUID participantId);

    List<ParticipantCredential> findByParticipantIdAndCredentialTypeOrderByAssignedAtAsc(
        UUID participantId,
        ParticipantCredential.CredentialType credentialType
    );

    boolean existsByRaceIdAndCredentialTypeAndCredentialValueAndParticipantIdNot(
        UUID raceId,
        ParticipantCredential.CredentialType credentialType,
        String credentialValue,
        UUID participantId
    );
}
