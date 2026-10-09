package com.racetracker.repository;

import com.racetracker.model.ParticipantPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantPenaltyRepository extends JpaRepository<ParticipantPenalty, UUID> {

    List<ParticipantPenalty> findByRaceIdAndParticipantIdOrderByCreatedAtAsc(UUID raceId, UUID participantId);

    List<ParticipantPenalty> findByParticipantIdIn(java.util.Collection<UUID> participantIds);

    Optional<ParticipantPenalty> findByRaceIdAndId(UUID raceId, UUID id);

    @Query("select coalesce(sum(pp.seconds), 0) from ParticipantPenalty pp where pp.participant.id = :participantId")
    int sumSecondsByParticipantId(@Param("participantId") UUID participantId);
}
