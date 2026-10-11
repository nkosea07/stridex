package com.racetracker.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

@Component
public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final int CLOCK_WINDOW = 1;
    private final SecureRandom secureRandom = new SecureRandom();

    public String newSecret() {
        byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public boolean verify(String base32Secret, String suppliedCode, Instant now) {
        if (suppliedCode == null || !suppliedCode.matches("\\d{6}")) {
            return false;
        }
        long currentCounter = now.getEpochSecond() / PERIOD_SECONDS;
        byte[] supplied = suppliedCode.getBytes(StandardCharsets.US_ASCII);
        for (long offset = -CLOCK_WINDOW; offset <= CLOCK_WINDOW; offset++) {
            byte[] expected = code(base32Secret, currentCounter + offset).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, supplied)) {
                return true;
            }
        }
        return false;
    }

    public String provisioningUri(String email, String secret) {
        String issuer = "Race Tracker";
        String label = URLEncoder.encode(issuer + ":" + email, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encodedIssuer
            + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    String codeAt(String base32Secret, long epochSeconds) {
        return code(base32Secret, epochSeconds / PERIOD_SECONDS);
    }

    private String code(String base32Secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(base32Secret), "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24)
                | ((digest[offset + 1] & 0xff) << 16)
                | ((digest[offset + 2] & 0xff) << 8)
                | (digest[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%0" + DIGITS + "d", binary % (int) Math.pow(10, DIGITS));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid TOTP secret", ex);
        }
    }

    private String encodeBase32(byte[] bytes) {
        StringBuilder result = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 0x1f));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        }
        return result.toString();
    }

    private byte[] decodeBase32(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(java.util.Locale.ROOT);
        byte[] output = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char character : normalized.toCharArray()) {
            int digit = BASE32.indexOf(character);
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid Base32 character");
            }
            buffer = (buffer << 5) | digit;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return index == output.length ? output : java.util.Arrays.copyOf(output, index);
    }
}
