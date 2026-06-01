package com.racetracker.repository;

import com.racetracker.model.Checkpoint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckpointRepository extends JpaRepository<Checkpoint, UUID> {

    @EntityGraph(attributePaths = {"records"})
    List<Checkpoint> findByRaceIdOrderByOrderIndexAsc(UUID raceId);

    Optional<Checkpoint> findByRaceIdAndId(UUID raceId, UUID id);

    @Query(value = """
        select exists (
            select 1
            from checkpoints c
            where c.race_id = :raceId
              and c.id = :checkpointId
              and c.location_geog is not null
              and ST_DWithin(
                  c.location_geog,
                  CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography),
                  :meters
              )
        )
    """, nativeQuery = true)
    boolean isCheckpointWithinMeters(
        @Param("raceId") UUID raceId,
        @Param("checkpointId") UUID checkpointId,
        @Param("latitude") BigDecimal latitude,
        @Param("longitude") BigDecimal longitude,
        @Param("meters") int meters
    );
}
