package com.racetracker.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies short-lived HMAC-signed tokens that allow anonymous access
 * to a single athlete's certificate without exposing enumerable identifiers.
 */
@Component
public class CertificateTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long DEFAULT_TTL_SECONDS = 60 * 60 * 24 * 14;

    private final byte[] secret;

    public CertificateTokenService(@Value("${jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(UUID raceId, UUID participantId) {
        long expiresAt = Instant.now().getEpochSecond() + DEFAULT_TTL_SECONDS;
        String payload = raceId + ":" + participantId + ":" + expiresAt;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public Optional<Claims> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int separator = token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            return Optional.empty();
        }
        String encodedPayload = token.substring(0, separator);
        String signature = token.substring(separator + 1);
        if (!constantTimeEquals(sign(encodedPayload), signature)) {
            return Optional.empty();
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            String[] parts = payload.split(":");
            if (parts.length != 3) {
                return Optional.empty();
            }
            long expiresAt = Long.parseLong(parts[2]);
            if (expiresAt < Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(new Claims(UUID.fromString(parts[0]), UUID.fromString(parts[1])));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign certificate token", ex);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    public record Claims(UUID raceId, UUID participantId) {}
}
