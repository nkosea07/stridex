-- RFID timing integrity for real reader hardware.

-- Reads that are kept for audit and later reprocessing but never become a timing record
-- (before the gun, reader clock ahead of the server). Added in its own statement because a
-- new enum value cannot be used in the transaction that adds it.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_enum
        WHERE enumlabel = 'IGNORED' AND enumtypid = 'rfid_read_event_status'::regtype
    ) THEN
        ALTER TYPE rfid_read_event_status ADD VALUE 'IGNORED';
    END IF;
END
$$;

-- Actual gun time. Reads before it are not timing reads. Set when the race moves to
-- IN_PROGRESS and correctable by a race admin.
ALTER TABLE races ADD COLUMN started_at TIMESTAMPTZ;

-- Gateway clock offset measured from heartbeats (gateway clock minus server clock).
ALTER TABLE rfid_readers ADD COLUMN clock_offset_ms BIGINT;

-- Canonical RFID tag values: hex EPCs are stored upper-case without separators so reads from
-- readers that format EPCs differently still match. Rows whose canonical value would collide
-- with another row are left unchanged and reported by scripts/release-gate.sql.
UPDATE participant_credentials c
SET credential_value = upper(regexp_replace(c.credential_value, '[[:space:]:-]', '', 'g')),
    updated_at = CURRENT_TIMESTAMP
WHERE c.credential_type = 'RFID_UHF_TIMING'
  AND regexp_replace(c.credential_value, '[[:space:]:-]', '', 'g') ~ '^[0-9A-Fa-f]+$'
  AND c.credential_value <> upper(regexp_replace(c.credential_value, '[[:space:]:-]', '', 'g'))
  AND NOT EXISTS (
      SELECT 1 FROM participant_credentials o
      WHERE o.race_id = c.race_id
        AND o.credential_type = c.credential_type
        AND o.id <> c.id
        AND upper(regexp_replace(o.credential_value, '[[:space:]:-]', '', 'g'))
            = upper(regexp_replace(c.credential_value, '[[:space:]:-]', '', 'g'))
  );

UPDATE participants p
SET rfid_tag = upper(regexp_replace(p.rfid_tag, '[[:space:]:-]', '', 'g'))
WHERE p.rfid_tag IS NOT NULL
  AND regexp_replace(p.rfid_tag, '[[:space:]:-]', '', 'g') ~ '^[0-9A-Fa-f]+$'
  AND p.rfid_tag <> upper(regexp_replace(p.rfid_tag, '[[:space:]:-]', '', 'g'))
  AND NOT EXISTS (
      SELECT 1 FROM participants o
      WHERE o.race_id = p.race_id
        AND o.id <> p.id
        AND o.rfid_tag IS NOT NULL
        AND upper(regexp_replace(o.rfid_tag, '[[:space:]:-]', '', 'g'))
            = upper(regexp_replace(p.rfid_tag, '[[:space:]:-]', '', 'g'))
  );

CREATE INDEX idx_rfid_read_events_race_status_tag
    ON rfid_read_events (race_id, status, rfid_tag);
