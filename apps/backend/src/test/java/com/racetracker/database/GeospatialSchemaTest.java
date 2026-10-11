package com.racetracker.database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GeospatialSchemaTest {

    @Test
    void schemaDefinesPostgisSpatialColumnsIndexesAndPartitioningPath() throws IOException {
        String schema = Files.readString(Path.of("database/schema.sql"))
            .replaceAll("\\s+", " ")
            .toLowerCase();

        assertThat(schema).contains("create extension if not exists postgis");
        assertThat(schema).contains("start_location_geog geography(point, 4326)");
        assertThat(schema).contains("finish_location_geog geography(point, 4326)");
        assertThat(schema).contains("location_geog geography(point, 4326)");
        assertThat(schema).contains("on races using gist (start_location_geog)");
        assertThat(schema).contains("on races using gist (finish_location_geog)");
        assertThat(schema).contains("on checkpoints using gist (location_geog)");
        assertThat(schema).contains("on gps_locations using gist (location_geog)");
        assertThat(schema).contains("future partitioning path");
        assertThat(schema).contains("range partitioning on recorded_at");
        assertThat(schema).contains("create or replace function set_race_location_geog()");
        assertThat(schema).contains("create trigger set_race_location_geog_before_write");
        assertThat(schema).contains("create or replace function set_checkpoint_location_geog()");
        assertThat(schema).contains("create trigger set_checkpoint_location_geog_before_write");
        assertThat(schema).contains("create or replace function set_gps_location_geog()");
        assertThat(schema).contains("create trigger set_gps_location_geog_before_write");
    }

    @Test
    void geospatialRolloutMigrationKeepsSpatialColumnsMaintained() throws IOException {
        String migration = Files.readString(Path.of("database/migrations/2026-06-01-geospatial-telemetry-foundation.sql"))
            .replaceAll("\\s+", " ")
            .toLowerCase();

        assertThat(migration).contains("create extension if not exists postgis");
        assertThat(migration).contains("add column if not exists start_location_geog geography(point, 4326)");
        assertThat(migration).contains("add column if not exists finish_location_geog geography(point, 4326)");
        assertThat(migration).contains("add column if not exists location_geog geography(point, 4326)");
        assertThat(migration).contains("create or replace function set_race_location_geog()");
        assertThat(migration).contains("drop trigger if exists set_race_location_geog_before_write on races");
        assertThat(migration).contains("create trigger set_checkpoint_location_geog_before_write");
        assertThat(migration).contains("create trigger set_gps_location_geog_before_write");
        assertThat(migration).contains("on gps_locations using gist (location_geog)");
        assertThat(migration).contains("range partitioning on recorded_at");
    }
}
