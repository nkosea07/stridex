package com.racetracker.repository;

import com.racetracker.model.RaceRoutePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RaceRoutePointRepository extends JpaRepository<RaceRoutePoint, UUID> {

    List<RaceRoutePoint> findByRaceIdOrderBySequenceAsc(UUID raceId);

    @Modifying
    @Query("delete from RaceRoutePoint point where point.race.id = :raceId")
    void deleteByRaceId(@Param("raceId") UUID raceId);
}
