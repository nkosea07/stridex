package com.racetracker.repository;

import com.racetracker.model.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    List<ImportJob> findByRaceIdOrderByCreatedAtDesc(UUID raceId);
}
