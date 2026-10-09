package com.racetracker.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayPostgresMigrationSmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
    void migratesFreshPostgisDatabaseThroughCurrentVersion() throws Exception {
        String url = System.getenv("FLYWAY_TEST_URL");
        String username = System.getenv().getOrDefault("FLYWAY_TEST_USERNAME", "");
        String password = System.getenv().getOrDefault("FLYWAY_TEST_PASSWORD", "");

        Flyway flyway = Flyway.configure()
            .dataSource(url, username, password)
            .locations("classpath:db/migration")
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .load();

        var result = flyway.migrate();
        flyway.validate();
        assertThat(result.success).isTrue();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("13");

        try (var connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            try (ResultSet postgis = statement.executeQuery("SELECT extversion FROM pg_extension WHERE extname = 'postgis'")) {
                assertThat(postgis.next()).isTrue();
            }
            try (ResultSet index = statement.executeQuery("""
                SELECT 1 FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND indexname = 'idx_rfid_read_events_source_unique'
                """)) {
                assertThat(index.next()).isTrue();
            }
        }
    }
}
