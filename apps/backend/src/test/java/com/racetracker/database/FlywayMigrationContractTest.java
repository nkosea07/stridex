package com.racetracker.database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationContractTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    @Test
    void migrationsAreVersionedAndBaselineDoesNotCreateDemoAdmin() throws IOException {
        assertThat(Files.exists(MIGRATION_DIR.resolve("V1__baseline.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V2__add_received_rfid_event_status.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V3__organizer_applications_and_rfid_scale.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V4__privileged_mfa_state.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V5__organizer_application_review.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V6__append_only_audit_events.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V7__race_live_feed_revision.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V8__result_publication_and_read_indexes.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V9__race_result_revision.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V10__registration_approval_statuses.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V11__registration_workflows.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V12__registration_waitlist_unique.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V13__live_tracking_enhancements.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V34__role_capabilities_and_race_staff.sql"))).isTrue();

        String baseline = Files.readString(MIGRATION_DIR.resolve("V1__baseline.sql")).toLowerCase();
        assertThat(baseline).doesNotContain("admin@racetracker.com", "admin123");
    }

    @Test
    void capabilityMigrationDoesNotWidenCheckpointVolunteerAssignments() throws IOException {
        String migration = Files.readString(
            MIGRATION_DIR.resolve("V34__role_capabilities_and_race_staff.sql")
        ).toLowerCase();

        assertThat(migration).doesNotContain("insert into race_staff_assignments");
        assertThat(migration).contains("does not backfill race_staff_assignments");
        assertThat(migration).doesNotContain("duplicate_object", "if not exists");
    }

    @Test
    void rfidIdempotencyMigrationChecksDuplicatesBeforeCreatingUniqueIndex() throws IOException {
        String migration = Files.readString(
            MIGRATION_DIR.resolve("V3__organizer_applications_and_rfid_scale.sql")
        ).toLowerCase();

        assertThat(migration).contains("raise exception 'rfid idempotency migration blocked");
        assertThat(migration).contains("using hint = 'export and reconcile");
        assertThat(migration.indexOf("raise exception 'rfid idempotency migration blocked"))
            .isLessThan(migration.indexOf("create unique index if not exists idx_rfid_read_events_source_unique"));
        assertThat(migration).doesNotContain("delete from rfid_read_events");
    }
}
