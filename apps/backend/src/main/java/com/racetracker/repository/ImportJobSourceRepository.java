package com.racetracker.repository;

import com.racetracker.model.ImportJobSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImportJobSourceRepository extends JpaRepository<ImportJobSource, UUID> {
}
