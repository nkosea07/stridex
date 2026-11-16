package com.racetracker.repository;

import com.racetracker.model.RaceWave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceWaveRepository extends JpaRepository<RaceWave, UUID> {

    List<RaceWave> findByRaceIdOrderBySortOrderAscNameAsc(UUID raceId);

    Optional<RaceWave> findByRaceIdAndId(UUID raceId, UUID id);

    boolean existsByRaceIdAndNameIgnoreCase(UUID raceId, String name);
}
