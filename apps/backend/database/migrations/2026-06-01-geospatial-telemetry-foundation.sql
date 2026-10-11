-- Geospatial telemetry foundation migration.
-- Keeps existing decimal latitude/longitude columns as the API compatibility source of truth
-- and maintains PostGIS geography query fields for proximity operations.

CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE races
    ADD COLUMN IF NOT EXISTS start_location_geog geography(Point, 4326),
    ADD COLUMN IF NOT EXISTS finish_location_geog geography(Point, 4326);

ALTER TABLE checkpoints
    ADD COLUMN IF NOT EXISTS location_geog geography(Point, 4326);

ALTER TABLE gps_locations
    ADD COLUMN IF NOT EXISTS location_geog geography(Point, 4326);

UPDATE races
SET start_location_geog = ST_SetSRID(ST_MakePoint(start_location_lng::double precision, start_location_lat::double precision), 4326)::geography
WHERE start_location_lat IS NOT NULL
  AND start_location_lng IS NOT NULL
  AND start_location_geog IS NULL;

UPDATE races
SET finish_location_geog = ST_SetSRID(ST_MakePoint(finish_location_lng::double precision, finish_location_lat::double precision), 4326)::geography
WHERE finish_location_lat IS NOT NULL
  AND finish_location_lng IS NOT NULL
  AND finish_location_geog IS NULL;

UPDATE checkpoints
SET location_geog = ST_SetSRID(ST_MakePoint(longitude::double precision, latitude::double precision), 4326)::geography
WHERE latitude IS NOT NULL
  AND longitude IS NOT NULL
  AND location_geog IS NULL;

UPDATE gps_locations
SET location_geog = ST_SetSRID(ST_MakePoint(longitude::double precision, latitude::double precision), 4326)::geography
WHERE location_geog IS NULL;

CREATE OR REPLACE FUNCTION set_race_location_geog()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.start_location_lat IS NOT NULL AND NEW.start_location_lng IS NOT NULL THEN
        NEW.start_location_geog = ST_SetSRID(ST_MakePoint(NEW.start_location_lng::double precision, NEW.start_location_lat::double precision), 4326)::geography;
    ELSE
        NEW.start_location_geog = NULL;
    END IF;

    IF NEW.finish_location_lat IS NOT NULL AND NEW.finish_location_lng IS NOT NULL THEN
        NEW.finish_location_geog = ST_SetSRID(ST_MakePoint(NEW.finish_location_lng::double precision, NEW.finish_location_lat::double precision), 4326)::geography;
    ELSE
        NEW.finish_location_geog = NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS set_race_location_geog_before_write ON races;
CREATE TRIGGER set_race_location_geog_before_write
    BEFORE INSERT OR UPDATE OF start_location_lat, start_location_lng, finish_location_lat, finish_location_lng
    ON races
    FOR EACH ROW
    EXECUTE FUNCTION set_race_location_geog();

CREATE OR REPLACE FUNCTION set_checkpoint_location_geog()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        NEW.location_geog = ST_SetSRID(ST_MakePoint(NEW.longitude::double precision, NEW.latitude::double precision), 4326)::geography;
    ELSE
        NEW.location_geog = NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS set_checkpoint_location_geog_before_write ON checkpoints;
CREATE TRIGGER set_checkpoint_location_geog_before_write
    BEFORE INSERT OR UPDATE OF latitude, longitude
    ON checkpoints
    FOR EACH ROW
    EXECUTE FUNCTION set_checkpoint_location_geog();

CREATE OR REPLACE FUNCTION set_gps_location_geog()
RETURNS TRIGGER AS $$
BEGIN
    NEW.location_geog = ST_SetSRID(ST_MakePoint(NEW.longitude::double precision, NEW.latitude::double precision), 4326)::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS set_gps_location_geog_before_write ON gps_locations;
CREATE TRIGGER set_gps_location_geog_before_write
    BEFORE INSERT OR UPDATE OF latitude, longitude
    ON gps_locations
    FOR EACH ROW
    EXECUTE FUNCTION set_gps_location_geog();

CREATE INDEX IF NOT EXISTS idx_races_start_location_geog
    ON races USING GIST (start_location_geog);

CREATE INDEX IF NOT EXISTS idx_races_finish_location_geog
    ON races USING GIST (finish_location_geog);

CREATE INDEX IF NOT EXISTS idx_checkpoints_location_geog
    ON checkpoints USING GIST (location_geog);

CREATE INDEX IF NOT EXISTS idx_gps_locations_location_geog
    ON gps_locations USING GIST (location_geog);

-- Future partitioning path:
-- Convert gps_locations to RANGE partitioning on recorded_at in a dedicated migration.
-- This migration keeps the existing table shape stable for current JPA/API compatibility.
