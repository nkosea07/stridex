package com.racetracker.repository;

import com.racetracker.model.Race;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceRepository extends JpaRepository<Race, UUID> {

    @EntityGraph(attributePaths = {"categories", "participants"})
    Page<Race> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"categories", "participants"})
    Page<Race> findByStatus(Race.RaceStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"categories", "participants"})
    Page<Race> findByRaceDateGreaterThanEqual(LocalDate raceDate, Pageable pageable);

    @EntityGraph(attributePaths = {"categories", "participants"})
    Page<Race> findByStatusAndRaceDateGreaterThanEqual(Race.RaceStatus status, LocalDate raceDate, Pageable pageable);

    @EntityGraph(attributePaths = {"categories", "participants"})
    Page<Race> findAllByArchivedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"categories", "participants"})
    Page<Race> findByStatusAndArchivedAtIsNull(Race.RaceStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"categories", "participants"})
    Page<Race> findByRaceDateGreaterThanEqualAndArchivedAtIsNull(LocalDate raceDate, Pageable pageable);

    @EntityGraph(attributePaths = {"categories", "participants"})
    Page<Race> findByStatusAndRaceDateGreaterThanEqualAndArchivedAtIsNull(Race.RaceStatus status, LocalDate raceDate, Pageable pageable);

    long countByStatusIn(Collection<Race.RaceStatus> statuses);

    List<Race> findByStatusAndRaceDateBefore(Race.RaceStatus status, LocalDate raceDate);

    long countByRaceDateBetween(LocalDate from, LocalDate to);

    @Override
    @EntityGraph(attributePaths = {"categories", "participants"})
    Optional<Race> findById(UUID id);

    @EntityGraph(attributePaths = {"categories", "participants"})
    @Query("""
        select r from Race r
        where (:ownerId is null
               or r.createdBy.id = :ownerId
               or exists (select 1 from Race rr join rr.admins a where rr.id = r.id and a.id = :ownerId))
          and (:status is null or r.status = :status)
          and (:upcoming = false or r.raceDate >= :today)
          and (:includeArchived = true or r.archivedAt is null)
    """)
    Page<Race> search(
        @Param("ownerId") UUID ownerId,
        @Param("status") Race.RaceStatus status,
        @Param("upcoming") boolean upcoming,
        @Param("includeArchived") boolean includeArchived,
        @Param("today") LocalDate today,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Race r where r.id = :id")
    Optional<Race> findByIdForUpdate(@Param("id") UUID id);
}
