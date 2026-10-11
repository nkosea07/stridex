-- Live tracking enhancements: race-scoped athlete follows (favorites), GPS drift
-- filtering/smoothing metadata, and a race+time index for track/heatmap queries.

CREATE TABLE IF NOT EXISTS athlete_follows (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES participants(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, participant_id)
);

CREATE INDEX IF NOT EXISTS idx_athlete_follows_user ON athlete_follows(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_athlete_follows_race_participant ON athlete_follows(race_id, participant_id);

ALTER TABLE gps_locations
    ADD COLUMN IF NOT EXISTS filtered BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS filter_reason VARCHAR(40),
    ADD COLUMN IF NOT EXISTS smoothed_latitude NUMERIC(10, 8),
    ADD COLUMN IF NOT EXISTS smoothed_longitude NUMERIC(11, 8);

CREATE INDEX IF NOT EXISTS idx_gps_locations_race_recorded ON gps_locations(race_id, recorded_at);
