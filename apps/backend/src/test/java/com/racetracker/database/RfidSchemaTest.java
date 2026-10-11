package com.racetracker.database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RfidSchemaTest {

    @Test
    void schemaDefinesRfidReaderAndReadEventTables() throws IOException {
        String schema = Files.readString(Path.of("database/schema.sql"))
            .replaceAll("\\s+", " ")
            .toLowerCase();

        assertThat(schema).contains("create type rfid_reader_status as enum ('active', 'revoked')");
        assertThat(schema).contains("create type rfid_gateway_status as enum ('online', 'degraded', 'offline')");
        assertThat(schema).contains("create type rfid_read_event_status as enum ('received', 'accepted', 'duplicate', 'unknown_tag', 'invalid_reader', 'error')");
        assertThat(schema).contains("create type participant_credential_type as enum ('rfid_uhf_timing', 'qr_ticket', 'nfc_gate', 'barcode_gatepass')");
        assertThat(schema).contains("create table rfid_readers");
        assertThat(schema).contains("api_key_hash varchar(64) unique not null");
        assertThat(schema).contains("gateway_status rfid_gateway_status default 'offline'");
        assertThat(schema).contains("hardware_profile varchar(120)");
        assertThat(schema).contains("reader_class varchar(120)");
        assertThat(schema).contains("unique(race_id, external_id)");
        assertThat(schema).contains("create table participant_credentials");
        assertThat(schema).contains("create table rfid_read_events");
        assertThat(schema).contains("matched_participant_id uuid references participants(id)");
        assertThat(schema).contains("checkpoint_record_id uuid references checkpoint_records(id)");
        assertThat(schema).contains("create index idx_rfid_readers_api_key_hash");
        assertThat(schema).contains("create index idx_rfid_read_events_source");
        assertThat(schema).contains("create index idx_participants_rfid_tag");
        assertThat(schema).contains("create index idx_participant_credentials_lookup");
    }

    @Test
    void manualMigrationDefinesRfidRolloutSql() throws IOException {
        String migration = Files.readString(Path.of("database/migrations/2026-06-02-rfid-bib-ingest-foundation.sql"))
            .replaceAll("\\s+", " ")
            .toLowerCase();

        assertThat(migration).contains("create table rfid_readers");
        assertThat(migration).contains("create table rfid_read_events");
        assertThat(migration).contains("create index idx_rfid_readers_api_key_hash");
        assertThat(migration).contains("create trigger update_rfid_readers_updated_at");

        String scalingMigration = Files.readString(Path.of("database/migrations/2026-06-02-rfid-scale-foundation.sql"))
            .replaceAll("\\s+", " ")
            .toLowerCase();

        assertThat(scalingMigration).contains("create table if not exists participant_credentials");
        assertThat(scalingMigration).contains("alter type rfid_read_event_status add value 'received' before 'accepted'");
        assertThat(scalingMigration).contains("add column if not exists gateway_status rfid_gateway_status default 'offline'");
        assertThat(scalingMigration).contains("add column if not exists hardware_profile varchar(120)");
        assertThat(scalingMigration).contains("add column if not exists reader_class varchar(120)");
        assertThat(scalingMigration).contains("insert into participant_credentials");
    }
}
