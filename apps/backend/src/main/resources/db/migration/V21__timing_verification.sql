-- Phase 1 (2026-10-11): timing verification and persisted results.
--
-- races.completed_at records the verified completion instant.
-- checkpoint_records.is_cutoff_missed flags a read that arrived after the checkpoint cutoff.
-- race_results gains the columns needed to snapshot official results faithfully.

ALTER TABLE races
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE checkpoint_records
    ADD COLUMN IF NOT EXISTS is_cutoff_missed BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_checkpoint_records_cutoff_missed
    ON checkpoint_records (race_id)
    WHERE is_cutoff_missed;

ALTER TABLE race_results
    ADD COLUMN IF NOT EXISTS age_group VARCHAR(20);

ALTER TABLE race_results
    ADD COLUMN IF NOT EXISTS age_group_position INTEGER;

ALTER TABLE race_results
    ADD COLUMN IF NOT EXISTS penalty_seconds INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_race_results_race_position
    ON race_results (race_id, overall_position);
