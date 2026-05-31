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

    @Query("""
        select g
        from GpsLocation g
        where g.race.id = :raceId
          and g.participant.id in :participantIds
          and g.recordedAt = (
            select max(g2.recordedAt)
            from GpsLocation g2
            where g2.race.id = :raceId
              and g2.participant.id = g.participant.id
          )
    """)
    List<GpsLocation> findLatestByRaceIdAndParticipantIds(
        @Param("raceId") UUID raceId,
        @Param("participantIds") List<UUID> participantIds
    );

    List<GpsLocation> findByRaceIdAndRecordedAtAfterOrderByRecordedAtDesc(UUID raceId, OffsetDateTime since);

    @Query("select count(distinct g.participant.id) from GpsLocation g where g.recordedAt >= :since")
    long countDistinctParticipantsActiveSince(@Param("since") OffsetDateTime since);
}
