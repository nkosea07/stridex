package com.racetracker.repository;

import com.racetracker.model.RaceRegistrationField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceRegistrationFieldRepository extends JpaRepository<RaceRegistrationField, UUID> {

    List<RaceRegistrationField> findByRaceIdOrderByDisplayOrderAsc(UUID raceId);

    List<RaceRegistrationField> findByRaceIdAndActiveTrueOrderByDisplayOrderAsc(UUID raceId);

    Optional<RaceRegistrationField> findByRaceIdAndId(UUID raceId, UUID id);

    Optional<RaceRegistrationField> findByRaceIdAndFieldKey(UUID raceId, String fieldKey);
}
