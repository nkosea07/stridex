package com.racetracker.repository;

import com.racetracker.model.RaceStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceStageRepository extends JpaRepository<RaceStage, UUID> {
    List<RaceStage> findByRaceIdOrderBySequenceAsc(UUID raceId);
    Optional<RaceStage> findByRaceIdAndId(UUID raceId, UUID id);
    boolean existsByRaceIdAndSequence(UUID raceId, Integer sequence);
}
