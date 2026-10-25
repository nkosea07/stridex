package com.racetracker.repository;

import com.racetracker.model.ImportJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    List<ImportJob> findByRaceIdOrderByCreatedAtDesc(UUID raceId);

    Optional<ImportJob> findByRaceIdAndId(UUID raceId, UUID id);

    List<ImportJob> findByStatusIn(Collection<ImportJob.JobStatus> statuses);

    @EntityGraph(attributePaths = {"race", "createdBy"})
    @Query("select j from ImportJob j where j.id = :id")
    Optional<ImportJob> findWithRaceById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"race"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ImportJob j where j.id = :id")
    Optional<ImportJob> findByIdForUpdate(@Param("id") UUID id);
}
