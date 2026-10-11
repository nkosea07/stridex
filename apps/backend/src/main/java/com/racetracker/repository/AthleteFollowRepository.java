package com.racetracker.repository;

import com.racetracker.model.AthleteFollow;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AthleteFollowRepository extends JpaRepository<AthleteFollow, UUID> {

    @EntityGraph(attributePaths = {"race", "participant"})
    List<AthleteFollow> findByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndParticipantId(UUID userId, UUID participantId);

    Optional<AthleteFollow> findByUserIdAndParticipantId(UUID userId, UUID participantId);

    @EntityGraph(attributePaths = {"user"})
    List<AthleteFollow> findByRaceIdAndParticipantId(UUID raceId, UUID participantId);

    void deleteByUserId(UUID userId);
}
