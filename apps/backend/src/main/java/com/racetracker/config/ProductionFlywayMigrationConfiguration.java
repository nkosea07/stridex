package com.racetracker.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("prod")
public class ProductionFlywayMigrationConfiguration {

    @Bean
    FlywayMigrationStrategy validateSecretsBeforeMigrations(ProductionConfigurationValidator validator) {
        return (Flyway flyway) -> {
            validator.validateConfiguration();
            flyway.migrate();
        };
    }
}
