package com.racetracker.repository;

import com.racetracker.model.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {
    Optional<AuthToken> findByToken(String token);
    Optional<AuthToken> findByTokenAndTokenTypeAndConsumedAtIsNull(String token, AuthToken.TokenType tokenType);
    Optional<AuthToken> findByTokenAndTokenType(String token, AuthToken.TokenType tokenType);

    @Modifying
    @Query("update AuthToken t set t.consumedAt = :now where t.id = :id and t.consumedAt is null")
    int consumeIfActive(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("update AuthToken t set t.consumedAt = :now where t.familyId = :familyId and t.tokenType = :tokenType and t.consumedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("tokenType") AuthToken.TokenType tokenType,
                     @Param("now") OffsetDateTime now);
    List<AuthToken> findByUserIdAndTokenTypeAndConsumedAtIsNull(UUID userId, AuthToken.TokenType tokenType);
    void deleteByExpiresAtBefore(OffsetDateTime cutoff);
    void deleteByUserId(UUID userId);
}
