package com.racetracker.repository;

import com.racetracker.model.Participant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    @EntityGraph(attributePaths = {"race", "category", "user"})
    List<Participant> findByRaceIdOrderByRegisteredAtDesc(UUID raceId);

    @EntityGraph(attributePaths = {"race", "category", "user"})
    List<Participant> findByRaceId(UUID raceId);

    @EntityGraph(attributePaths = {"race", "category", "user"})
    Optional<Participant> findByRaceIdAndId(UUID raceId, UUID id);

    @EntityGraph(attributePaths = {"race", "category", "user"})
    Optional<Participant> findByRaceIdAndUserId(UUID raceId, UUID userId);

    @EntityGraph(attributePaths = {"race", "category", "user"})
    List<Participant> findByUserIdOrderByRegisteredAtDesc(UUID userId);

    boolean existsByRaceIdAndUserId(UUID raceId, UUID userId);

    Optional<Participant> findByRaceIdAndBibNumber(UUID raceId, String bibNumber);

    @Modifying
    @Query("UPDATE Participant p SET p.totalDistance = COALESCE(p.totalDistance, 0.0) + :delta WHERE p.id = :id")
    void incrementTotalDistance(@Param("id") UUID id, @Param("delta") BigDecimal delta);
}
