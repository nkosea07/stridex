package com.racetracker.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class MfaSecretCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String configuredKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public MfaSecretCipher(@Value("${racetracker.mfa.encryption-key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    public String encrypt(String secret) {
        byte[] key = keyBytes();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encrypt MFA secret", ex);
        }
    }

    public String decrypt(String encryptedSecret) {
        byte[] key = keyBytes();
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedSecret);
            if (payload.length <= NONCE_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted MFA secret");
            }
            byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not decrypt MFA secret", ex);
        }
    }

    private byte[] keyBytes() {
        try {
            byte[] bytes = Base64.getDecoder().decode(configuredKey);
            if (bytes.length != 32) {
                throw new IllegalStateException("MFA_ENCRYPTION_KEY must be base64-encoded 32-byte key material");
            }
            return bytes;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("MFA_ENCRYPTION_KEY must be base64-encoded 32-byte key material", ex);
        }
    }
}
