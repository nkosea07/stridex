package com.racetracker.security;

import com.racetracker.config.JwtProperties;
import com.racetracker.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    void init() {
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        return generateAccessToken(user, false);
    }

    public String generateAccessToken(User user, boolean mfaVerified) {
        return buildToken(user, jwtProperties.getAccessTokenExpiration(), TOKEN_TYPE_ACCESS, mfaVerified, null, null);
    }

    public String generateRefreshToken(User user) {
        return generateRefreshToken(user, false);
    }

    public String generateRefreshToken(User user, boolean mfaVerified) {
        return buildToken(user, jwtProperties.getRefreshTokenExpiration(), TOKEN_TYPE_REFRESH, mfaVerified, null, null);
    }

    public String generateMfaChallengeToken(User user, UUID challengeId, String purpose) {
        return buildToken(user, java.time.Duration.ofMinutes(5).toMillis(), "mfa_challenge", false, challengeId, purpose);
    }

    public boolean isMfaVerified(String token) {
        try {
            return Boolean.TRUE.equals(extractAllClaims(token).get("mfa", Boolean.class));
        } catch (Exception ex) {
            return false;
        }
    }

    public UUID extractMfaChallengeId(String token) {
        String value = extractAllClaims(token).get("cid", String.class);
        return value == null ? null : UUID.fromString(value);
    }

    public String extractMfaPurpose(String token) {
        return extractAllClaims(token).get("purpose", String.class);
    }

    public String extractTokenType(String token) {
        return extractAllClaims(token).get("typ", String.class);
    }

    public boolean hasTokenType(String token, String expectedType) {
        try {
            String tokenType = extractTokenType(token);
            return expectedType.equals(tokenType);
        } catch (Exception ex) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        String value = extractAllClaims(token).get("uid", String.class);
        return value == null ? null : UUID.fromString(value);
    }

    public OffsetDateTime extractExpiration(String token) {
        return extractAllClaims(token).getExpiration().toInstant().atOffset(ZoneOffset.UTC);
    }

    public boolean isTokenValid(String token, org.springframework.security.core.userdetails.UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username != null
                && username.equals(userDetails.getUsername())
                && userDetails.isEnabled()
                && userDetails.isAccountNonExpired()
                && userDetails.isAccountNonLocked()
                && userDetails.isCredentialsNonExpired()
                && !isTokenExpired(token);
        } catch (Exception ex) {
            return false;
        }
    }

    private String buildToken(
        User user,
        long expirationMillis,
        String tokenType,
        boolean mfaVerified,
        UUID challengeId,
        String challengePurpose
    ) {
        Instant now = Instant.now();
        var token = Jwts.builder()
            .subject(user.getEmail())
            .claim("uid", user.getId().toString())
            .claim("role", user.getRole().name())
            .claim("typ", tokenType)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMillis)));
        if (TOKEN_TYPE_ACCESS.equals(tokenType) || TOKEN_TYPE_REFRESH.equals(tokenType)) {
            token.claim("mfa", mfaVerified);
        }
        if (challengeId != null) {
            token.claim("cid", challengeId.toString()).claim("purpose", challengePurpose);
        }
        return token.signWith(signingKey).compact();
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        return expiration.before(new Date());
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (SecurityException ex) {
            throw ex;
        }
    }
}
