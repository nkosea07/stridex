-- Race clone and archive support.
-- Archived races stay in the database for reporting but are hidden from default listings.

ALTER TABLE races
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_races_archived_at ON races(archived_at);
