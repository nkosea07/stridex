package com.racetracker.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Secrets printed in checkpoint self-scan QR codes. The payload is
 * {@code checkpointId:raceId:token}; the token proves the athlete read the printed code rather
 * than calling the API with identifiers that are visible to every registered athlete.
 */
public final class SelfScanTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SelfScanTokens() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static boolean matches(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), provided.trim().getBytes(StandardCharsets.UTF_8));
    }

    public static String qrPayload(UUID checkpointId, UUID raceId, String token) {
        return checkpointId + ":" + raceId + ":" + token;
    }
}
