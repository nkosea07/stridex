package com.racetracker.repository;

import com.racetracker.model.MfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    Optional<MfaRecoveryCode> findByUserIdAndCodeHashAndUsedAtIsNull(UUID userId, String codeHash);

    @Modifying
    @Query("update MfaRecoveryCode code set code.usedAt = :now where code.id = :id and code.userId = :userId and code.usedAt is null")
    int markUsed(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("update MfaRecoveryCode code set code.usedAt = :now where code.userId = :userId and code.usedAt is null")
    int invalidateAll(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
