ALTER TABLE races
    ADD COLUMN results_published_at TIMESTAMPTZ;

CREATE INDEX idx_participants_race_result_order
    ON participants (race_id, bib_number, registered_at, id);

CREATE INDEX idx_gps_locations_race_participant_recorded
    ON gps_locations (race_id, participant_id, recorded_at DESC);

CREATE INDEX idx_checkpoint_records_race_participant_recorded
    ON checkpoint_records (race_id, participant_id, recorded_at ASC);
