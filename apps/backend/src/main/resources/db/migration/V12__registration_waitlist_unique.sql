-- Guard against duplicate waitlist positions per race. The application assigns
-- positions as max(position)+1 under a race-row lock, but a database-level partial
-- unique index keeps concurrent writers from producing collisions.
CREATE UNIQUE INDEX IF NOT EXISTS idx_participants_race_waitlist_position_unique
    ON participants(race_id, waitlist_position)
    WHERE status = 'WAITLISTED' AND waitlist_position IS NOT NULL;
