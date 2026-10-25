package com.racetracker.repository;

import com.racetracker.model.ParticipantRegistrationAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRegistrationAnswerRepository extends JpaRepository<ParticipantRegistrationAnswer, UUID> {

    List<ParticipantRegistrationAnswer> findByParticipantId(UUID participantId);

    Optional<ParticipantRegistrationAnswer> findByParticipantIdAndFieldId(UUID participantId, UUID fieldId);

    void deleteByParticipantId(UUID participantId);

    void deleteByParticipantIdIn(List<UUID> participantIds);
}
