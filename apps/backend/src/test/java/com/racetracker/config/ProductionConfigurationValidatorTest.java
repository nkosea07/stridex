package com.racetracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTest {

    @Test
    void run_acceptsExplicitHttpsOriginAndStrongSecret() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of("https://app.racetracker.example"));

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
            "a-strong-production-jwt-secret-with-more-than-thirty-two-characters",
            true,
            corsProperties,
            "jdbc:postgresql://db.internal:5432/race_tracker",
            "unique-production-database-password",
            false,
            false,
            "",
            false,
            false,
            productionStorage()
        );

        assertThatCode(() -> validator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void run_rejectsLocalhostCorsOrigin() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of("http://localhost:5173"));

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
            "a-strong-production-jwt-secret-with-more-than-thirty-two-characters",
            true,
            corsProperties,
            "jdbc:postgresql://db.internal:5432/race_tracker",
            "unique-production-database-password",
            false,
            false,
            "",
            false,
            false,
            productionStorage()
        );

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Production CORS");
    }

    @Test
    void run_rejectsCheckedInEnvironmentTemplateSecrets() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of("https://app.racetracker.example"));

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
            "CHANGE_ME_RANDOM_SECRET_AT_LEAST_32_CHARACTERS",
            true,
            corsProperties,
            "jdbc:postgresql://db.internal:5432/race_tracker",
            "unique-production-database-password",
            false,
            false,
            "",
            false,
            false,
            productionStorage()
        );

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void run_rejectsPlaceholderDatabasePassword() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of("https://app.racetracker.example"));

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
            "a-strong-production-jwt-secret-with-more-than-thirty-two-characters",
            true,
            corsProperties,
            "jdbc:postgresql://db.internal:5432/race_tracker",
            "CHANGE_ME_DATABASE_PASSWORD",
            false,
            false,
            "",
            false,
            false,
            productionStorage()
        );

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void run_rejectsEnabledOAuthInProduction() {
        RaceTrackerProperties properties = productionStorage();
        properties.getOauth().setEnabled(true);

        assertThatThrownBy(() -> newValidator(validCorsProperties(), properties)
            .run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("disable OAuth sign-in");
    }

    @Test
    void run_rejectsDevelopmentSeedUsersInProduction() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
            "a-strong-production-jwt-secret-with-more-than-thirty-two-characters",
            true,
            validCorsProperties(),
            "jdbc:postgresql://db.internal:5432/race_tracker",
            "unique-production-database-password",
            false,
            false,
            "",
            false,
            true,
            productionStorage()
        );

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("disable all development seed users");
    }

    @Test
    void run_rejectsWildcardCorsHeaders() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of("https://app.racetracker.example"));
        corsProperties.setAllowedHeaders(List.of("*"));

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
            "a-strong-production-jwt-secret-with-more-than-thirty-two-characters",
            true,
            corsProperties,
            "jdbc:postgresql://db.internal:5432/race_tracker",
            "unique-production-database-password",
            false,
            false,
            "",
            false,
            false,
            productionStorage()
        );

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("wildcard headers");
    }

    @Test
    void run_acceptsAwsAndMinioEnabledTogetherWhenAwsIsSelected() {
        CorsProperties corsProperties = validCorsProperties();
        RaceTrackerProperties properties = productionStorage();
        properties.getStorage().getMinio().setEnabled(true);

        ProductionConfigurationValidator validator = newValidator(corsProperties, properties);

        assertThatCode(() -> validator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void run_acceptsConfiguredMinioInProduction() {
        RaceTrackerProperties properties = productionStorage();
        properties.getStorage().setProvider("minio");
        properties.getStorage().getAws().setEnabled(false);
        properties.getStorage().getMinio().setEnabled(true);
        properties.getStorage().getMinio().setBucket("race-media");
        properties.getStorage().getMinio().setEndpoint("https://minio.racetracker.example");
        properties.getStorage().getMinio().setAccessKey("minio-access");
        properties.getStorage().getMinio().setSecretKey("minio-secret");

        assertThatCode(() -> newValidator(validCorsProperties(), properties)
            .run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void run_rejectsLocalStorageInProduction() {
        RaceTrackerProperties properties = productionStorage();
        properties.getStorage().setProvider("local");

        assertThatThrownBy(() -> newValidator(validCorsProperties(), properties).run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("private AWS S3 or MinIO");
    }

    @Test
    void run_rejectsBlankStorageProviderInProduction() {
        RaceTrackerProperties properties = productionStorage();
        properties.getStorage().setProvider(" ");

        assertThatThrownBy(() -> newValidator(validCorsProperties(), properties).run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("private AWS S3 or MinIO");
    }

    @Test
    void run_rejectsSelectedDisabledStorageProvider() {
        RaceTrackerProperties properties = productionStorage();
        properties.getStorage().getAws().setEnabled(false);

        assertThatThrownBy(() -> newValidator(validCorsProperties(), properties).run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be enabled: aws-s3");
    }

    @Test
    void run_rejectsSelectedDisabledMinioProvider() {
        RaceTrackerProperties properties = productionStorage();
        properties.getStorage().setProvider("minio");
        properties.getStorage().getMinio().setEnabled(false);

        assertThatThrownBy(() -> newValidator(validCorsProperties(), properties).run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be enabled: minio");
    }

    @Test
    void run_rejectsLocalMinioEndpointInProduction() {
        RaceTrackerProperties properties = productionStorage();
        properties.getStorage().setProvider("minio");
        properties.getStorage().getMinio().setEnabled(true);
        properties.getStorage().getMinio().setBucket("race-media");
        properties.getStorage().getMinio().setEndpoint("https://localhost:9000");
        properties.getStorage().getMinio().setAccessKey("minio-access");
        properties.getStorage().getMinio().setSecretKey("minio-secret");

        assertThatThrownBy(() -> newValidator(validCorsProperties(), properties).run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must not point to localhost");
    }

    @Test
    void run_rejectsInsecureRemoteStorageEndpointInProduction() {
        RaceTrackerProperties properties = productionStorage();
        properties.getStorage().setProvider("minio");
        properties.getStorage().getMinio().setEnabled(true);
        properties.getStorage().getMinio().setBucket("race-media");
        properties.getStorage().getMinio().setEndpoint("http://minio.racetracker.example");
        properties.getStorage().getMinio().setAccessKey("minio-access");
        properties.getStorage().getMinio().setSecretKey("minio-secret");

        assertThatThrownBy(() -> newValidator(validCorsProperties(), properties).run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must use HTTPS");
    }

    private ProductionConfigurationValidator newValidator(
        CorsProperties corsProperties,
        RaceTrackerProperties properties
    ) {
        return new ProductionConfigurationValidator(
            "a-strong-production-jwt-secret-with-more-than-thirty-two-characters",
            true,
            corsProperties,
            "jdbc:postgresql://db.internal:5432/race_tracker",
            "unique-production-database-password",
            false,
            false,
            "",
            false,
            false,
            properties
        );
    }

    private CorsProperties validCorsProperties() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of("https://app.racetracker.example"));
        return corsProperties;
    }

    private RaceTrackerProperties productionStorage() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getStorage().setProvider("aws-s3");
        properties.getStorage().getAws().setEnabled(true);
        properties.getStorage().getAws().setBucket("race-media");
        properties.getStorage().getAws().setRegion("us-east-1");
        properties.getOauth().setEnabled(false);
        return properties;
    }
}
