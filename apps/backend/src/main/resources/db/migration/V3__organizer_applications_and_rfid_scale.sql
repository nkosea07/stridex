-- Organizer signup stays separate from privileged role assignment.
CREATE TABLE IF NOT EXISTS organizer_applications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    applicant_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    organization_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_notes VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT organizer_applications_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_organizer_applications_status
    ON organizer_applications(status, created_at);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'rfid_gateway_status') THEN
        CREATE TYPE rfid_gateway_status AS ENUM ('ONLINE', 'DEGRADED', 'OFFLINE');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'participant_credential_type') THEN
        CREATE TYPE participant_credential_type AS ENUM ('RFID_UHF_TIMING', 'QR_TICKET', 'NFC_GATE', 'BARCODE_GATEPASS');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'participant_credential_status') THEN
        CREATE TYPE participant_credential_status AS ENUM ('ACTIVE', 'REVOKED');
    END IF;
END
$$;

ALTER TABLE rfid_readers
    ADD COLUMN IF NOT EXISTS gateway_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS gateway_status rfid_gateway_status DEFAULT 'OFFLINE',
    ADD COLUMN IF NOT EXISTS gateway_backlog INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS firmware_version VARCHAR(120),
    ADD COLUMN IF NOT EXISTS hardware_model VARCHAR(200),
    ADD COLUMN IF NOT EXISTS hardware_profile VARCHAR(120),
    ADD COLUMN IF NOT EXISTS reader_class VARCHAR(120),
    ADD COLUMN IF NOT EXISTS last_error TEXT,
    ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_processed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE rfid_read_events
    ADD COLUMN IF NOT EXISTS event_origin VARCHAR(20) NOT NULL DEFAULT 'HARDWARE';

CREATE TABLE IF NOT EXISTS participant_credentials (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    participant_id UUID REFERENCES participants(id) ON DELETE CASCADE NOT NULL,
    race_id UUID REFERENCES races(id) ON DELETE CASCADE NOT NULL,
    credential_type participant_credential_type NOT NULL,
    credential_value VARCHAR(200) NOT NULL,
    status participant_credential_status NOT NULL DEFAULT 'ACTIVE',
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(race_id, credential_type, credential_value)
);

CREATE INDEX IF NOT EXISTS idx_participant_credentials_participant ON participant_credentials(participant_id);
CREATE INDEX IF NOT EXISTS idx_participant_credentials_lookup
    ON participant_credentials(race_id, credential_type, credential_value, status);

INSERT INTO participant_credentials (participant_id, race_id, credential_type, credential_value, status, assigned_at)
SELECT p.id,
       p.race_id,
       'RFID_UHF_TIMING'::participant_credential_type,
       p.rfid_tag,
       'ACTIVE'::participant_credential_status,
       COALESCE(p.registered_at, CURRENT_TIMESTAMP)
FROM participants p
WHERE p.rfid_tag IS NOT NULL
  AND NULLIF(TRIM(p.rfid_tag), '') IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM participant_credentials pc
      WHERE pc.participant_id = p.id
        AND pc.credential_type = 'RFID_UHF_TIMING'::participant_credential_type
        AND pc.credential_value = p.rfid_tag
  );

DROP TRIGGER IF EXISTS update_participant_credentials_updated_at ON participant_credentials;
CREATE TRIGGER update_participant_credentials_updated_at
    BEFORE UPDATE ON participant_credentials
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Do not discard or merge timing data to make this constraint pass. Operators must
-- reconcile the reported source-event groups, then rerun the migration.
DO $$
DECLARE
    duplicate_count BIGINT;
    duplicate_examples TEXT;
BEGIN
    SELECT COUNT(*)
      INTO duplicate_count
    FROM (
        SELECT reader_id, source_event_id, COUNT(*) AS row_count
        FROM rfid_read_events
        WHERE reader_id IS NOT NULL AND source_event_id IS NOT NULL
        GROUP BY reader_id, source_event_id
        HAVING COUNT(*) > 1
    ) duplicates;

    SELECT STRING_AGG(format('reader=%s source_event_id=%s rows=%s', reader_id, source_event_id, row_count), '; ')
      INTO duplicate_examples
    FROM (
        SELECT reader_id, source_event_id, COUNT(*) AS row_count
        FROM rfid_read_events
        WHERE reader_id IS NOT NULL AND source_event_id IS NOT NULL
        GROUP BY reader_id, source_event_id
        HAVING COUNT(*) > 1
        ORDER BY COUNT(*) DESC
        LIMIT 10
    ) duplicates;

    IF duplicate_count > 0 THEN
        RAISE EXCEPTION 'RFID idempotency migration blocked: % duplicate (reader_id, source_event_id) groups. Examples: %',
            duplicate_count, duplicate_examples
            USING HINT = 'Export and reconcile the listed event groups without deleting timing records, then rerun this migration.';
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_rfid_read_events_source_unique
    ON rfid_read_events(reader_id, source_event_id)
    WHERE source_event_id IS NOT NULL;
