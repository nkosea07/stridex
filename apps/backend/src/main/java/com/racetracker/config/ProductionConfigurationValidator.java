package com.racetracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.net.URI;

@Component
@Profile("prod")
public class ProductionConfigurationValidator implements ApplicationRunner {

    private static final int MINIMUM_JWT_SECRET_LENGTH = 32;

    private final String jwtSecret;
    private final boolean browserCookieSecure;
    private final CorsProperties corsProperties;
    private final String databaseUrl;
    private final String databasePassword;
    private final boolean rfidSimulationEnabled;
    private final boolean pushEnabled;
    private final String pushServiceAccountJson;
    private final boolean defaultAdminSeedEnabled;
    private final boolean localUsersSeedEnabled;
    private final RaceTrackerProperties raceTrackerProperties;

    public ProductionConfigurationValidator(
        @Value("${jwt.secret:}") String jwtSecret,
        @Value("${racetracker.auth.browser-cookie-secure:false}") boolean browserCookieSecure,
        CorsProperties corsProperties,
        @Value("${spring.datasource.url:}") String databaseUrl,
        @Value("${spring.datasource.password:}") String databasePassword,
        @Value("${racetracker.rfid.simulation-enabled:true}") boolean rfidSimulationEnabled,
        @Value("${racetracker.push.enabled:false}") boolean pushEnabled,
        @Value("${racetracker.push.service-account-json:}") String pushServiceAccountJson,
        @Value("${racetracker.seed.default-admin.enabled:false}") boolean defaultAdminSeedEnabled,
        @Value("${racetracker.seed.local-users.enabled:false}") boolean localUsersSeedEnabled,
        RaceTrackerProperties raceTrackerProperties
    ) {
        this.jwtSecret = jwtSecret;
        this.browserCookieSecure = browserCookieSecure;
        this.corsProperties = corsProperties;
        this.databaseUrl = databaseUrl;
        this.databasePassword = databasePassword;
        this.rfidSimulationEnabled = rfidSimulationEnabled;
        this.pushEnabled = pushEnabled;
        this.pushServiceAccountJson = pushServiceAccountJson;
        this.defaultAdminSeedEnabled = defaultAdminSeedEnabled;
        this.localUsersSeedEnabled = localUsersSeedEnabled;
        this.raceTrackerProperties = raceTrackerProperties;
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
        if (defaultAdminSeedEnabled || localUsersSeedEnabled) {
            throw new IllegalStateException("Production must disable all development seed users");
        }

        if (jwtSecret.isBlank() || jwtSecret.length() < MINIMUM_JWT_SECRET_LENGTH || isPlaceholder(jwtSecret)
            || jwtSecret.contains("local-development")) {
            throw new IllegalStateException("Production requires a JWT_SECRET with at least 32 non-default characters");
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
        if (raceTrackerProperties.getOauth() != null && raceTrackerProperties.getOauth().isEnabled()) {
            throw new IllegalStateException("Production must disable OAuth sign-in; use password authentication");
        }

        if (pushEnabled && (pushServiceAccountJson == null || pushServiceAccountJson.isBlank())) {
            throw new IllegalStateException("Production push notifications require FCM_SERVICE_ACCOUNT_JSON");
        }
        RaceTrackerProperties.Notifications notifications = raceTrackerProperties.getNotifications();
        if (notifications != null && notifications.isSmsEnabled()) {
            throw new IllegalStateException("SMS notifications are disabled in this release; set SMS_ENABLED=false");
        }
        if (notifications != null && notifications.isWhatsappEnabled()
            && (isBlank(notifications.getWhatsappPhoneNumberId())
                || isBlank(notifications.getWhatsappAccessToken())
                || isBlank(notifications.getWhatsappAppSecret())
                || isBlank(notifications.getWhatsappVerifyToken()))) {
            throw new IllegalStateException("WhatsApp requires phone number id, access token, app secret, and verify token");
        }

        if (corsProperties.getAllowedOrigins().isEmpty() || corsProperties.getAllowedOrigins().stream().anyMatch(this::isUnsafeOrigin)) {
            throw new IllegalStateException("Production CORS must use explicit HTTPS origins and must not include localhost or wildcard origins");
        }
        if (corsProperties.getAllowedHeaders().stream().anyMatch("*"::equals)) {
            throw new IllegalStateException("Production CORS must explicitly allow request headers; wildcard headers are not permitted");
        }

        validateStorageConfiguration();
    }

    private void validateStorageConfiguration() {
        RaceTrackerProperties.Storage storage = raceTrackerProperties.getStorage();
        String provider = storage == null || storage.getProvider() == null
            ? "" : storage.getProvider().trim().toLowerCase(java.util.Locale.ROOT);

        switch (provider) {
            case "aws-s3" -> {
                RaceTrackerProperties.Aws aws = storage.getAws();
                if (aws == null || !aws.isEnabled()) {
                    throw new IllegalStateException("Production selected media storage provider must be enabled: aws-s3");
                }
                if (aws.getBucket() == null || aws.getBucket().isBlank()) {
                    throw new IllegalStateException("Production AWS S3 media storage requires a bucket");
                }
                validateOptionalStorageEndpoint(aws.getEndpoint(), "AWS S3");
            }
            case "minio" -> {
                RaceTrackerProperties.Minio minio = storage.getMinio();
                if (minio == null || !minio.isEnabled()) {
                    throw new IllegalStateException("Production selected media storage provider must be enabled: minio");
                }
                if (minio.getBucket() == null || minio.getBucket().isBlank()
                    || minio.getEndpoint() == null || minio.getEndpoint().isBlank()
                    || minio.getAccessKey() == null || minio.getAccessKey().isBlank()
                    || minio.getSecretKey() == null || minio.getSecretKey().isBlank()) {
                    throw new IllegalStateException("Production MinIO media storage requires endpoint, bucket, access key, and secret key");
                }
                validateStorageEndpoint(minio.getEndpoint(), "MinIO");
            }
            case "", "local" -> throw new IllegalStateException(
                "Production requires a configured private AWS S3 or MinIO media storage provider"
            );
            default -> throw new IllegalStateException("Unsupported production media storage provider: " + provider);
        }
    }

    private void validateOptionalStorageEndpoint(String endpoint, String provider) {
        if (endpoint != null && !endpoint.isBlank()) {
            validateStorageEndpoint(endpoint, provider);
        }
    }

    private void validateStorageEndpoint(String endpoint, String provider) {
        try {
            URI uri = URI.create(endpoint.trim());
            String host = uri.getHost();
            if (host == null || !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalStateException("Production " + provider + " storage endpoint must use HTTPS");
            }
            if (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1")) {
                throw new IllegalStateException("Production " + provider + " storage endpoint must not point to localhost");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Production " + provider + " storage endpoint must use HTTPS");
        }
    }

    private boolean isPlaceholder(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("change_me") || normalized.equals("changeme")
            || normalized.contains("${") || normalized.startsWith("your-");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isUnsafeOrigin(String origin) {
        return origin == null
            || origin.isBlank()
            || origin.contains("localhost")
            || origin.contains("*")
            || !origin.startsWith("https://");
    }
}
