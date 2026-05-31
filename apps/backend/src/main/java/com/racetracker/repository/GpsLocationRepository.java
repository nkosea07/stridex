package com.racetracker.repository;

import com.racetracker.model.GpsLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GpsLocationRepository extends JpaRepository<GpsLocation, UUID> {

    List<GpsLocation> findByParticipantIdAndRaceIdOrderByRecordedAtAsc(UUID participantId, UUID raceId);

    Optional<GpsLocation> findTopByParticipantIdAndRaceIdOrderByRecordedAtDesc(UUID participantId, UUID raceId);

    List<GpsLocation> findByRaceIdAndRecordedAtAfterOrderByRecordedAtDesc(UUID raceId, OffsetDateTime since);

    @Query("select count(distinct g.participant.id) from GpsLocation g where g.recordedAt >= :since")
    long countDistinctParticipantsActiveSince(@Param("since") OffsetDateTime since);
}
