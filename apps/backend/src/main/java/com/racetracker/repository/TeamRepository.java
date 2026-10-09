package com.racetracker.repository;

import com.racetracker.model.Team;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    @EntityGraph(attributePaths = {"race", "captainUser"})
    List<Team> findByRaceIdOrderByNameAsc(UUID raceId);

    @EntityGraph(attributePaths = {"race", "captainUser"})
    Optional<Team> findByRaceIdAndId(UUID raceId, UUID id);

    Optional<Team> findByRaceIdAndNameIgnoreCase(UUID raceId, String name);
}
