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
        return buildToken(user, jwtProperties.getAccessTokenExpiration(), TOKEN_TYPE_ACCESS);
    }

    public String generateRefreshToken(User user) {
        return buildToken(user, jwtProperties.getRefreshTokenExpiration(), TOKEN_TYPE_REFRESH);
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

    public boolean isTokenValid(String token, org.springframework.security.core.userdetails.UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username != null
                && username.equals(userDetails.getUsername())
                && !isTokenExpired(token);
        } catch (Exception ex) {
            return false;
        }
    }

    private String buildToken(User user, long expirationMillis, String tokenType) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getEmail())
            .claim("uid", user.getId().toString())
            .claim("role", user.getRole().name())
            .claim("typ", tokenType)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMillis)))
            .signWith(signingKey)
            .compact();
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
