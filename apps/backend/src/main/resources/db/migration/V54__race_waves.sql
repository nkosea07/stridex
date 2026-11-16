-- Wave (corral) starts: each wave has its own gun time, and a participant's gun and chip
-- times, start gate, and cutoffs use their wave's start.

CREATE TABLE race_waves (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    scheduled_start TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_race_waves_name UNIQUE (race_id, name)
);

CREATE INDEX idx_race_waves_race_order ON race_waves (race_id, sort_order);

ALTER TABLE participants ADD COLUMN wave_id UUID REFERENCES race_waves(id) ON DELETE RESTRICT;
CREATE INDEX idx_participants_wave ON participants (wave_id) WHERE wave_id IS NOT NULL;

CREATE INDEX idx_rfid_read_events_participant_checkpoint_read
    ON rfid_read_events (matched_participant_id, checkpoint_id, read_at)
    WHERE matched_participant_id IS NOT NULL;
