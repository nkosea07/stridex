package com.racetracker.repository;

import com.racetracker.model.MfaChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, UUID> {

    Optional<MfaChallenge> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update MfaChallenge challenge set challenge.consumedAt = :now " +
        "where challenge.id = :id and challenge.userId = :userId " +
        "and challenge.purpose = :purpose and challenge.consumedAt is null and challenge.expiresAt > :now")
    int consumeActive(
        @Param("id") UUID id,
        @Param("userId") UUID userId,
        @Param("purpose") String purpose,
        @Param("now") OffsetDateTime now
    );
}
