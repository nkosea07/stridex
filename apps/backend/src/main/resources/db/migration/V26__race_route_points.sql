-- Phase 8 (2026-10-11): race route geometry imported from GPX.

CREATE TABLE IF NOT EXISTS race_route_points (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    latitude NUMERIC(10, 8) NOT NULL,
    longitude NUMERIC(11, 8) NOT NULL,
    elevation NUMERIC(8, 2)
);

CREATE INDEX IF NOT EXISTS idx_race_route_points_race ON race_route_points(race_id, sequence);
