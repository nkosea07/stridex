package com.racetracker.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaSecretCipherTest {

    @Test
    void encryptsSecretsWithAuthenticatedEncryptionAndRoundTrips() {
        MfaSecretCipher cipher = new MfaSecretCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

        String encrypted = cipher.encrypt("BASE32-TOTP-SECRET");

        assertThat(encrypted).isNotEqualTo("BASE32-TOTP-SECRET");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("BASE32-TOTP-SECRET");
    }

    @Test
    void rejectsMissingOrInvalidKeyMaterial() {
        MfaSecretCipher cipher = new MfaSecretCipher("");

        assertThatThrownBy(() -> cipher.encrypt("secret"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("MFA_ENCRYPTION_KEY");
    }
}
