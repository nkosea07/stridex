package com.racetracker.repository;

import com.racetracker.model.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {
    Optional<AuthToken> findByToken(String token);
    Optional<AuthToken> findByTokenAndTokenTypeAndConsumedAtIsNull(String token, AuthToken.TokenType tokenType);
    List<AuthToken> findByUserIdAndTokenTypeAndConsumedAtIsNull(UUID userId, AuthToken.TokenType tokenType);
    void deleteByExpiresAtBefore(OffsetDateTime cutoff);
    void deleteByUserId(UUID userId);
}
