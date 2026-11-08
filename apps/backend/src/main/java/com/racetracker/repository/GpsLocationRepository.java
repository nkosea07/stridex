package com.racetracker.repository;

import com.racetracker.model.GpsLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GpsLocationRepository extends JpaRepository<GpsLocation, UUID> {

    List<GpsLocation> findByParticipantIdAndRaceIdOrderByRecordedAtAsc(UUID participantId, UUID raceId);

    Optional<GpsLocation> findTopByParticipantIdAndRaceIdAndFilteredFalseOrderByRecordedAtDesc(UUID participantId, UUID raceId);

    @Query("""
        select g
        from GpsLocation g
        where g.race.id = :raceId
          and g.participant.id in :participantIds
          and g.filtered = false
          and g.recordedAt = (
            select max(g2.recordedAt)
            from GpsLocation g2
            where g2.race.id = :raceId
              and g2.participant.id = g.participant.id
              and g2.filtered = false
          )
    """)
    List<GpsLocation> findLatestByRaceIdAndParticipantIds(
        @Param("raceId") UUID raceId,
        @Param("participantIds") List<UUID> participantIds
    );

    List<GpsLocation> findByRaceIdAndRecordedAtAfterOrderByRecordedAtDesc(UUID raceId, OffsetDateTime since);

    @Query("select count(distinct g.participant.id) from GpsLocation g where g.recordedAt >= :since")
    long countDistinctParticipantsActiveSince(@Param("since") OffsetDateTime since);

    @Query("select g.participant.id, count(g) from GpsLocation g where g.race.id = :raceId and g.filtered = true group by g.participant.id")
    List<Object[]> countFilteredByRace(@Param("raceId") UUID raceId);

    @Modifying
    @Query("delete from GpsLocation g where g.recordedAt < :cutoff")
    int deleteByRecordedAtBefore(@Param("cutoff") OffsetDateTime cutoff);

    @Query(value = """
        select round(coalesce(g.smoothed_latitude, g.latitude)::numeric, :scale)::double precision as latitude,
               round(coalesce(g.smoothed_longitude, g.longitude)::numeric, :scale)::double precision as longitude,
               count(*) as weight
        from gps_locations g
        join participants p on p.id = g.participant_id
        where g.race_id = :raceId
          and g.filtered = false
          and coalesce(p.is_tracking_enabled, true) = true
          and (:from is null or g.recorded_at >= :from)
          and (:to is null or g.recorded_at <= :to)
        group by 1, 2
        order by weight desc
        """, nativeQuery = true)
    List<HeatCellProjection> heatCells(
        @Param("raceId") UUID raceId,
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to,
        @Param("scale") int scale
    );

    void deleteByParticipantIdIn(java.util.Collection<UUID> participantIds);
}
