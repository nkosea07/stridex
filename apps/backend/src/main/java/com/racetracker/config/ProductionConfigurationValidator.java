package com.racetracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.util.Base64;

@Component
@Profile("prod")
public class ProductionConfigurationValidator implements ApplicationRunner {

    private static final int MINIMUM_JWT_SECRET_LENGTH = 32;

    private final String jwtSecret;
    private final String mfaEncryptionKey;
    private final boolean browserCookieSecure;
    private final CorsProperties corsProperties;
    private final String databaseUrl;
    private final String databasePassword;
    private final boolean rfidSimulationEnabled;

    public ProductionConfigurationValidator(
        @Value("${jwt.secret:}") String jwtSecret,
        @Value("${racetracker.mfa.encryption-key:}") String mfaEncryptionKey,
        @Value("${racetracker.auth.browser-cookie-secure:false}") boolean browserCookieSecure,
        CorsProperties corsProperties,
        @Value("${spring.datasource.url:}") String databaseUrl,
        @Value("${spring.datasource.password:}") String databasePassword,
        @Value("${racetracker.rfid.simulation-enabled:true}") boolean rfidSimulationEnabled
    ) {
        this.jwtSecret = jwtSecret;
        this.mfaEncryptionKey = mfaEncryptionKey;
        this.browserCookieSecure = browserCookieSecure;
        this.corsProperties = corsProperties;
        this.databaseUrl = databaseUrl;
        this.databasePassword = databasePassword;
        this.rfidSimulationEnabled = rfidSimulationEnabled;
    }

    @PostConstruct
    public void validateBeforeMigration() {
        validateConfiguration();
    }

    @Override
    public void run(ApplicationArguments args) {
        validateConfiguration();
    }

    public void validateConfiguration() {
        if (jwtSecret.isBlank() || jwtSecret.length() < MINIMUM_JWT_SECRET_LENGTH || isPlaceholder(jwtSecret)
            || jwtSecret.contains("local-development")) {
            throw new IllegalStateException("Production requires a JWT_SECRET with at least 32 non-default characters");
        }

        try {
            if (Base64.getDecoder().decode(mfaEncryptionKey).length != 32) {
                throw new IllegalStateException("Production requires MFA_ENCRYPTION_KEY as base64-encoded 32-byte key material");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Production requires MFA_ENCRYPTION_KEY as base64-encoded 32-byte key material");
        }

        if (!browserCookieSecure) {
            throw new IllegalStateException("Production browser refresh cookies must set Secure=true");
        }

        if (databaseUrl.isBlank() || databaseUrl.toLowerCase(java.util.Locale.ROOT).contains("localhost")
            || databaseUrl.contains("127.0.0.1")) {
            throw new IllegalStateException("Production requires an explicit non-local PostgreSQL/PostGIS connection URL");
        }
        if (databasePassword.isBlank() || isPlaceholder(databasePassword)
            || databasePassword.toLowerCase(java.util.Locale.ROOT).contains("localdev")
            || databasePassword.equalsIgnoreCase("password")) {
            throw new IllegalStateException("Production requires a non-development DB_PASSWORD");
        }
        if (rfidSimulationEnabled) {
            throw new IllegalStateException("Production must disable global RFID simulation; enable simulation only for authorized test races");
        }

        if (corsProperties.getAllowedOrigins().isEmpty() || corsProperties.getAllowedOrigins().stream().anyMatch(this::isUnsafeOrigin)) {
            throw new IllegalStateException("Production CORS must use explicit HTTPS origins and must not include localhost or wildcard origins");
        }
    }

    private boolean isPlaceholder(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("change_me") || normalized.equals("changeme")
            || normalized.contains("${") || normalized.startsWith("your-");
    }

    private boolean isUnsafeOrigin(String origin) {
        return origin == null
            || origin.isBlank()
            || origin.contains("localhost")
            || origin.contains("*")
            || !origin.startsWith("https://");
    }
}
