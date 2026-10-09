package com.racetracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface RaceResultRevisionRepository extends JpaRepository<com.racetracker.model.RaceResultRevision, UUID> {
    @Query(value = "select coalesce((select revision from race_result_revisions where race_id = :raceId), 0)", nativeQuery = true)
    long currentRevision(@Param("raceId") UUID raceId);
}
