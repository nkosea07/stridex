package com.racetracker.repository;

import com.racetracker.model.Checkpoint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckpointRepository extends JpaRepository<Checkpoint, UUID> {

    @EntityGraph(attributePaths = {"records"})
    List<Checkpoint> findByRaceIdOrderByOrderIndexAsc(UUID raceId);

    Optional<Checkpoint> findByRaceIdAndId(UUID raceId, UUID id);
}
