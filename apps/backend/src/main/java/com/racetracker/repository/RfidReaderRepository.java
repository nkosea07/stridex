package com.racetracker.repository;

import com.racetracker.model.RfidReader;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RfidReaderRepository extends JpaRepository<RfidReader, UUID> {

    @EntityGraph(attributePaths = {"race", "checkpoint"})
    Optional<RfidReader> findByApiKeyHash(String apiKeyHash);

    @EntityGraph(attributePaths = {"race", "checkpoint"})
    Optional<RfidReader> findByRaceIdAndId(UUID raceId, UUID id);

    @EntityGraph(attributePaths = {"race", "checkpoint"})
    List<RfidReader> findByRaceIdOrderByCreatedAtDesc(UUID raceId);

    boolean existsByRaceIdAndExternalId(UUID raceId, String externalId);
}
