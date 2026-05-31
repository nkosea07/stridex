package com.racetracker.repository;

import com.racetracker.model.RaceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RaceCategoryRepository extends JpaRepository<RaceCategory, UUID> {
    List<RaceCategory> findByRaceId(UUID raceId);
}
