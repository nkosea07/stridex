package com.racetracker.config;

import com.racetracker.security.AdminMfaPolicy;
import com.racetracker.security.MfaSecretCipher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Production must require two-factor sign-in for admins and hold a valid MFA encryption key. */
@Component
@Profile("prod")
public class AdminMfaConfigurationValidator implements ApplicationRunner {

    private final AdminMfaPolicy adminMfaPolicy;
    private final MfaSecretCipher mfaSecretCipher;

    public AdminMfaConfigurationValidator(AdminMfaPolicy adminMfaPolicy, MfaSecretCipher mfaSecretCipher) {
        this.adminMfaPolicy = adminMfaPolicy;
        this.mfaSecretCipher = mfaSecretCipher;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    public void validate() {
        if (!adminMfaPolicy.isRequired()) {
            throw new IllegalStateException("Production requires MFA_REQUIRED_FOR_ADMINS=true");
        }
        try {
            mfaSecretCipher.assertConfigured();
        } catch (IllegalStateException ex) {
            throw new IllegalStateException("Production requires MFA_ENCRYPTION_KEY (base64-encoded 32 bytes) for admin two-factor sign-in", ex);
        }
    }
}
