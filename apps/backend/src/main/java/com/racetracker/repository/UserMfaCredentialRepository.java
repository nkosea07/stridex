package com.racetracker.repository;

import com.racetracker.model.UserMfaCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserMfaCredentialRepository extends JpaRepository<UserMfaCredential, UUID> {
    void deleteByUserId(UUID userId);

    Optional<UserMfaCredential> findByUserIdAndVerifiedAtIsNotNull(UUID userId);
    boolean existsByUserIdAndVerifiedAtIsNotNull(UUID userId);
}
