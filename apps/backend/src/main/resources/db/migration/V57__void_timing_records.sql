-- Void wrongly attributed timing records (swapped chips, reads for the wrong athlete) without
-- destroying evidence. A voided record stays in the table, is excluded from results, and can be
-- restored. Only active records occupy a participant/checkpoint/pass slot.

ALTER TABLE checkpoint_records ADD COLUMN voided_at TIMESTAMPTZ;
ALTER TABLE checkpoint_records ADD COLUMN voided_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE checkpoint_records ADD COLUMN void_reason VARCHAR(500);

ALTER TABLE checkpoint_records DROP CONSTRAINT uq_checkpoint_records_participant_checkpoint_pass;
CREATE UNIQUE INDEX uq_checkpoint_records_active_pass
    ON checkpoint_records (participant_id, checkpoint_id, pass_number)
    WHERE voided_at IS NULL;
CREATE INDEX idx_checkpoint_records_voided ON checkpoint_records (race_id, checkpoint_id) WHERE voided_at IS NOT NULL;
CREATE INDEX idx_checkpoint_records_voided_by ON checkpoint_records (voided_by) WHERE voided_by IS NOT NULL;

ALTER TABLE checkpoint_record_corrections ADD COLUMN action VARCHAR(20) NOT NULL DEFAULT 'CORRECTED';
ALTER TABLE checkpoint_record_corrections ADD CONSTRAINT chk_checkpoint_record_corrections_action
    CHECK (action IN ('CORRECTED', 'VOIDED', 'RESTORED', 'REASSIGNED'));

-- Reads behind a voided record are kept as evidence but must never be re-run into a timing record
-- by a gun-time regate or reprocess.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_enum
        WHERE enumlabel = 'VOIDED' AND enumtypid = 'rfid_read_event_status'::regtype
    ) THEN
        ALTER TYPE rfid_read_event_status ADD VALUE 'VOIDED';
    END IF;
END
$$;
