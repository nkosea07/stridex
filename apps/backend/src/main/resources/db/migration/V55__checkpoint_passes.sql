-- Laps and repeated passes: a checkpoint can be crossed several times (loop courses, start and
-- finish on one mat). Each crossing is its own record numbered by pass.

ALTER TABLE checkpoints ADD COLUMN expected_passes INTEGER NOT NULL DEFAULT 1;
ALTER TABLE checkpoints ADD COLUMN min_pass_seconds INTEGER NOT NULL DEFAULT 60;
ALTER TABLE checkpoints ADD COLUMN first_pass_is_start BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE checkpoints ADD CONSTRAINT chk_checkpoints_expected_passes CHECK (expected_passes BETWEEN 1 AND 200);
ALTER TABLE checkpoints ADD CONSTRAINT chk_checkpoints_min_pass_seconds CHECK (min_pass_seconds >= 1);

ALTER TABLE checkpoint_records ADD COLUMN pass_number INTEGER NOT NULL DEFAULT 1;
ALTER TABLE checkpoint_records ADD CONSTRAINT chk_checkpoint_records_pass_number CHECK (pass_number >= 1);

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'checkpoint_records'::regclass
          AND c.contype = 'u'
          AND (
              SELECT array_agg(a.attname::text ORDER BY a.attname)
              FROM unnest(c.conkey) AS k(attnum)
              JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
          ) = ARRAY['checkpoint_id', 'participant_id']
    LOOP
        EXECUTE format('ALTER TABLE checkpoint_records DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END
$$;

ALTER TABLE checkpoint_records
    ADD CONSTRAINT uq_checkpoint_records_participant_checkpoint_pass UNIQUE (participant_id, checkpoint_id, pass_number);
