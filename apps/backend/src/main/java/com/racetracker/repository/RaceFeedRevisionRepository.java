package com.racetracker.repository;

import com.racetracker.model.RaceFeedRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface RaceFeedRevisionRepository extends JpaRepository<RaceFeedRevision, UUID> {

    @Query(value = "select coalesce((select revision from race_feed_revisions where race_id = :raceId), 0)", nativeQuery = true)
    long currentRevision(@Param("raceId") UUID raceId);
}
