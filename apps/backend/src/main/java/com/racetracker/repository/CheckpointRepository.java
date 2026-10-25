package com.racetracker.repository;

import com.racetracker.model.Checkpoint;
import com.racetracker.dto.checkpoint.OperationCheckpointDto;
import com.racetracker.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    boolean existsByRaceIdAndId(UUID raceId, UUID id);

    @Query(value = """
        select new com.racetracker.dto.checkpoint.OperationCheckpointDto(r.id, r.name, c.id, c.name)
        from Checkpoint c join c.race r
        where r.archivedAt is null and (
            exists (select v.id from Checkpoint assigned join assigned.volunteers v
                where assigned.id = c.id and lower(v.email) = lower(:email))
            or exists (select a.id from RaceStaffAssignment a
                where a.race = r and lower(a.user.email) = lower(:email)
                and a.staffRole = :role and a.active = true and a.revokedAt is null)
        )
        order by r.id, c.orderIndex, c.id
    """, countQuery = """
        select count(c)
        from Checkpoint c join c.race r
        where r.archivedAt is null and (
            exists (select v.id from Checkpoint assigned join assigned.volunteers v
                where assigned.id = c.id and lower(v.email) = lower(:email))
            or exists (select a.id from RaceStaffAssignment a
                where a.race = r and lower(a.user.email) = lower(:email)
                and a.staffRole = :role and a.active = true and a.revokedAt is null)
        )
    """)
    Page<OperationCheckpointDto> findOperationsForVolunteer(
        @Param("email") String email, @Param("role") User.UserRole role, Pageable pageable);

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
