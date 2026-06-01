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
    }
}
