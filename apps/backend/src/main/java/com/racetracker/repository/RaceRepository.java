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

    long countByStatusIn(Collection<Race.RaceStatus> statuses);

    long countByRaceDateBetween(LocalDate from, LocalDate to);

    @Override
    @EntityGraph(attributePaths = {"categories", "participants"})
    Optional<Race> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Race r where r.id = :id")
    Optional<Race> findByIdForUpdate(@Param("id") UUID id);
}
