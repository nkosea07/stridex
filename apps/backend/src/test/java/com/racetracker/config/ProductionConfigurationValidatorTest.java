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
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            true,
            corsProperties,
            "jdbc:postgresql://db.internal:5432/race_tracker",
            "unique-production-database-password",
            false,
            false,
            ""
        );

        assertThatCode(() -> validator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void run_rejectsLocalhostCorsOrigin() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of("http://localhost:5173"));

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
            "a-strong-production-jwt-secret-with-more-than-thirty-two-characters",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            true,
            corsProperties,
            "jdbc:postgresql://db.internal:5432/race_tracker",
            "unique-production-database-password",
            false,
            false,
            ""
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
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            true,
            corsProperties,
            "jdbc:postgresql://db.internal:5432/race_tracker",
            "unique-production-database-password",
            false,
            false,
            ""
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
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            true,
            corsProperties,
            "jdbc:postgresql://db.internal:5432/race_tracker",
            "CHANGE_ME_DATABASE_PASSWORD",
            false,
            false,
            ""
        );

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DB_PASSWORD");
    }
}
