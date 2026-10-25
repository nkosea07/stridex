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

    @Test
    @EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
    void upgradesFromV36ToCurrentVersion() throws Exception {
        String url = System.getenv("FLYWAY_TEST_URL");
        String username = System.getenv().getOrDefault("FLYWAY_TEST_USERNAME", "");
        String password = System.getenv().getOrDefault("FLYWAY_TEST_PASSWORD", "");

        Flyway baseline = Flyway.configure()
            .dataSource(url, username, password)
            .locations("classpath:db/migration")
            .validateOnMigrate(true)
            .cleanDisabled(false)
            .target("36")
            .load();
        baseline.clean();
        assertThat(baseline.migrate().success).isTrue();
        assertThat(baseline.info().current().getVersion().getVersion()).isEqualTo("36");

        String legacyAlertId = java.util.UUID.randomUUID().toString();
        String raceId = java.util.UUID.randomUUID().toString();
        try (var connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO races (id, name, race_type, race_date) VALUES ('"
                + raceId + "', 'Legacy SOS', 'MARATHON', CURRENT_DATE)");
            statement.execute("INSERT INTO emergency_alerts (id, race_id, message) VALUES ('"
                + legacyAlertId + "', '" + raceId + "', 'Legacy alert')");
        }

        Flyway upgrade = Flyway.configure()
            .dataSource(url, username, password)
            .locations("classpath:db/migration")
            .validateOnMigrate(true)
            .load();
        assertThat(upgrade.migrate().success).isTrue();
        upgrade.validate();

        String expectedHead = java.util.Arrays.stream(upgrade.info().all())
            .map(MigrationInfo::getVersion)
            .filter(Objects::nonNull)
            .map(version -> version.getVersion())
            .max(Comparator.comparingInt(Integer::parseInt))
            .orElseThrow();
        assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo(expectedHead);
        assertThat(Integer.parseInt(expectedHead)).isGreaterThanOrEqualTo(41);

        try (var connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            try (ResultSet legacy = statement.executeQuery(
                "SELECT delivery_status FROM emergency_alerts WHERE id = '" + legacyAlertId + "'")) {
                assertThat(legacy.next()).isTrue();
                assertThat(legacy.getString(1)).isEqualTo("LEGACY_UNVERIFIED");
            }
            statement.execute("INSERT INTO emergency_alerts (race_id, message) VALUES ('"
                + raceId + "', 'New alert')");
            try (ResultSet created = statement.executeQuery("""
                SELECT delivery_status FROM emergency_alerts
                WHERE message = 'New alert'
                """)) {
                assertThat(created.next()).isTrue();
                assertThat(created.getString(1)).isEqualTo("QUEUED");
            }
        }
    }
}
