-- gps_locations retention support.
--
-- Full range partitioning on recorded_at is a dedicated operations migration (it rewrites the
-- table) and is intentionally not performed here. This adds the index that both the retention
-- purge and any future partition routing rely on.

CREATE INDEX IF NOT EXISTS idx_gps_locations_recorded_at ON gps_locations (recorded_at);
