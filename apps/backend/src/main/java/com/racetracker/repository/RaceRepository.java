package com.racetracker.repository;

import com.racetracker.model.Race;
import com.racetracker.model.User;
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

    @EntityGraph(attributePaths = {"categories", "participants"})
    @Query("""
        select r from Race r
        where r.isPublicRegistration = true
          and r.status <> :draftStatus
          and r.archivedAt is null
          and (:upcoming = false or r.raceDate >= :today)
    """)
    Page<Race> searchForPublic(
        @Param("draftStatus") Race.RaceStatus draftStatus,
        @Param("upcoming") boolean upcoming,
        @Param("today") LocalDate today,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"categories", "participants"})
    @Query("""
        select r from Race r
        where r.isPublicRegistration = true
          and r.status <> :draftStatus
          and r.status = :status
          and r.archivedAt is null
          and (:upcoming = false or r.raceDate >= :today)
    """)
    Page<Race> searchForPublicByStatus(
        @Param("draftStatus") Race.RaceStatus draftStatus,
        @Param("status") Race.RaceStatus status,
        @Param("upcoming") boolean upcoming,
        @Param("today") LocalDate today,
        Pageable pageable
    );

    long countByStatusIn(Collection<Race.RaceStatus> statuses);

    List<Race> findByStatusAndRaceDateBefore(Race.RaceStatus status, LocalDate raceDate);

    long countByRaceDateBetween(LocalDate from, LocalDate to);

    @Override
    @EntityGraph(attributePaths = {"categories", "participants"})
    Optional<Race> findById(UUID id);

    @Query("""
        select distinct r from Race r
        left join fetch r.createdBy
        left join fetch r.admins
        where r.id = :id
    """)
    Optional<Race> findByIdForAuthorization(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"categories", "participants"})
    @Query("""
        select r from Race r
        where (r.createdBy.id = :ownerId
               or exists (select 1 from Race rr join rr.admins a where rr.id = r.id and a.id = :ownerId))
          and (:upcoming = false or r.raceDate >= :today)
          and (:includeArchived = true or r.archivedAt is null)
    """)
    Page<Race> searchForOwner(
        @Param("ownerId") UUID ownerId,
        @Param("upcoming") boolean upcoming,
        @Param("includeArchived") boolean includeArchived,
        @Param("today") LocalDate today,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"categories", "participants"})
    @Query("""
        select r from Race r
        where (r.createdBy.id = :ownerId
               or exists (select 1 from Race rr join rr.admins a where rr.id = r.id and a.id = :ownerId))
          and r.status = :status
          and (:upcoming = false or r.raceDate >= :today)
          and (:includeArchived = true or r.archivedAt is null)
    """)
    Page<Race> searchForOwnerByStatus(
        @Param("ownerId") UUID ownerId,
        @Param("status") Race.RaceStatus status,
        @Param("upcoming") boolean upcoming,
        @Param("includeArchived") boolean includeArchived,
        @Param("today") LocalDate today,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"categories", "participants"})
    @Query("""
        select r from Race r
        where exists (
            select 1 from RaceStaffAssignment a
            where a.race.id = r.id
              and a.user.id = :userId
              and a.staffRole = :staffRole
              and a.active = true
        )
          and (:upcoming = false or r.raceDate >= :today)
          and (:includeArchived = true or r.archivedAt is null)
    """)
    Page<Race> searchForStaff(
        @Param("userId") UUID userId,
        @Param("staffRole") User.UserRole staffRole,
        @Param("upcoming") boolean upcoming,
        @Param("includeArchived") boolean includeArchived,
        @Param("today") LocalDate today,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"categories", "participants"})
    @Query("""
        select r from Race r
        where exists (
            select 1 from RaceStaffAssignment a
            where a.race.id = r.id
              and a.user.id = :userId
              and a.staffRole = :staffRole
              and a.active = true
        )
          and r.status = :status
          and (:upcoming = false or r.raceDate >= :today)
          and (:includeArchived = true or r.archivedAt is null)
    """)
    Page<Race> searchForStaffByStatus(
        @Param("userId") UUID userId,
        @Param("staffRole") User.UserRole staffRole,
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
