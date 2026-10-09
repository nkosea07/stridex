package com.racetracker.repository;

import com.racetracker.model.RaceMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceMediaRepository extends JpaRepository<RaceMedia, UUID> {

    List<RaceMedia> findByRaceIdOrderByCreatedAtDesc(UUID raceId);

    List<RaceMedia> findByRaceIdAndStatusOrderByCreatedAtDesc(UUID raceId, RaceMedia.MediaStatus status);

    Optional<RaceMedia> findByRaceIdAndId(UUID raceId, UUID id);

    long countByRaceIdAndStatus(UUID raceId, RaceMedia.MediaStatus status);
}
