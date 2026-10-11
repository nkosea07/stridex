package com.racetracker.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayPostgresMigrationSmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
    void migratesFreshPostgisDatabaseThroughCurrentVersion() throws Exception {
        String url = System.getenv("FLYWAY_TEST_URL");
        String username = System.getenv().getOrDefault("FLYWAY_TEST_USERNAME", "");
        String password = System.getenv().getOrDefault("FLYWAY_TEST_PASSWORD", "");

        // The PostGIS image pre-installs geospatial extensions into the public schema, which makes
        // it non-empty and blocks a from-scratch Flyway migration. Remove them so the V1 baseline
        // can create the schema exactly as it would on any other fresh database.
        try (var connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP EXTENSION IF EXISTS postgis_tiger_geocoder CASCADE");
            statement.execute("DROP EXTENSION IF EXISTS postgis_topology CASCADE");
            statement.execute("DROP EXTENSION IF EXISTS fuzzystrmatch CASCADE");
            statement.execute("DROP EXTENSION IF EXISTS postgis CASCADE");
        }

        Flyway flyway = Flyway.configure()
            .dataSource(url, username, password)
            .locations("classpath:db/migration")
            .validateOnMigrate(true)
            .cleanDisabled(false)
            .load();

        // The target is a disposable test database: clean it so the smoke test is idempotent
        // and always proves a from-scratch migration.
        flyway.clean();

        var result = flyway.migrate();
        flyway.validate();
        assertThat(result.success).isTrue();

        String expectedHead = java.util.Arrays.stream(flyway.info().all())
            .map(MigrationInfo::getVersion)
            .filter(Objects::nonNull)
            .map(version -> version.getVersion())
            .max(Comparator.comparingInt(Integer::parseInt))
            .orElseThrow();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo(expectedHead);

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
