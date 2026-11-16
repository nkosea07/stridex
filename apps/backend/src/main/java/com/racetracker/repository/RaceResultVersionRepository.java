package com.racetracker.repository;

import com.racetracker.model.RaceResultVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceResultVersionRepository extends JpaRepository<RaceResultVersion, UUID> {

    Optional<RaceResultVersion> findTopByRaceIdOrderByVersionDesc(UUID raceId);

    List<RaceResultVersion> findByRaceIdOrderByVersionAsc(UUID raceId);
}
