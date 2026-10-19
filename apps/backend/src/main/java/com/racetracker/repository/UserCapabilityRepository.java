package com.racetracker.repository;

import com.racetracker.model.UserCapability;
import com.racetracker.model.UserCapabilityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserCapabilityRepository extends JpaRepository<UserCapability, UUID> {

    List<UserCapability> findByUserIdAndActiveTrueOrderByCapability(UUID userId);

    Optional<UserCapability> findByUserIdAndCapability(UUID userId, UserCapabilityType capability);
}
