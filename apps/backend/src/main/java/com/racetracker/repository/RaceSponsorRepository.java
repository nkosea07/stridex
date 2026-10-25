package com.racetracker.repository;

import com.racetracker.model.RaceSponsor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceSponsorRepository extends JpaRepository<RaceSponsor, UUID> {
    List<RaceSponsor> findByRaceIdOrderByDisplayOrderAsc(UUID raceId);
    Optional<RaceSponsor> findByRaceIdAndId(UUID raceId, UUID id);
}
