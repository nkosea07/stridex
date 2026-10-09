-- Timing corrections and penalties.
-- Checkpoint records can carry an official adjusted time while preserving the raw
-- recorded_at, and participants can receive time penalties that affect ranking.

CREATE TABLE IF NOT EXISTS participant_penalties (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES participants(id) ON DELETE CASCADE,
    seconds INTEGER NOT NULL,
    reason VARCHAR(255),
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_participant_penalties_participant
    ON participant_penalties(race_id, participant_id);

ALTER TABLE checkpoint_records
    ADD COLUMN IF NOT EXISTS adjusted_recorded_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS adjustment_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS adjusted_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS adjusted_at TIMESTAMP WITH TIME ZONE;
