-- Registration workflows: approval, manual payment verification, waivers, custom
-- questions, and team/club grouping. All changes are additive and keep the existing
-- participant_status values intact.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_status') THEN
        CREATE TYPE payment_status AS ENUM ('UNPAID', 'PENDING', 'PAID', 'WAIVED', 'REFUNDED');
    END IF;
END
$$;

ALTER TABLE races
    ADD COLUMN IF NOT EXISTS require_approval BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS waitlist_enabled BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS require_waiver BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS waiver_version VARCHAR(40),
    ADD COLUMN IF NOT EXISTS waiver_text TEXT;

CREATE TABLE IF NOT EXISTS teams (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    club_name VARCHAR(200),
    captain_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT teams_status_check CHECK (status IN ('OPEN', 'LOCKED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_teams_race_name_unique ON teams(race_id, lower(name));
CREATE INDEX IF NOT EXISTS idx_teams_race ON teams(race_id, created_at);

DROP TRIGGER IF EXISTS update_teams_updated_at ON teams;
CREATE TRIGGER update_teams_updated_at
    BEFORE UPDATE ON teams
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE participants
    ADD COLUMN IF NOT EXISTS shirt_size VARCHAR(20),
    ADD COLUMN IF NOT EXISTS waiver_accepted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS waiver_version VARCHAR(40),
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS approved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS waitlist_position INTEGER,
    ADD COLUMN IF NOT EXISTS payment_status payment_status DEFAULT 'UNPAID',
    ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(200),
    ADD COLUMN IF NOT EXISTS payment_amount NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS payment_verified_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS payment_verified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS team_id UUID REFERENCES teams(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_participants_race_status ON participants(race_id, status);
CREATE INDEX IF NOT EXISTS idx_participants_race_payment ON participants(race_id, payment_status);
CREATE INDEX IF NOT EXISTS idx_participants_race_team ON participants(race_id, team_id);

CREATE TABLE IF NOT EXISTS race_registration_fields (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    field_key VARCHAR(80) NOT NULL,
    label VARCHAR(200) NOT NULL,
    field_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    required BOOLEAN NOT NULL DEFAULT false,
    options JSONB,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT race_registration_fields_type_check CHECK (field_type IN ('TEXT', 'SELECT', 'CHECKBOX', 'NUMBER'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_race_registration_fields_key_unique
    ON race_registration_fields(race_id, field_key);
CREATE INDEX IF NOT EXISTS idx_race_registration_fields_race
    ON race_registration_fields(race_id, display_order);

DROP TRIGGER IF EXISTS update_race_registration_fields_updated_at ON race_registration_fields;
CREATE TRIGGER update_race_registration_fields_updated_at
    BEFORE UPDATE ON race_registration_fields
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE IF NOT EXISTS participant_registration_answers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    participant_id UUID NOT NULL REFERENCES participants(id) ON DELETE CASCADE,
    field_id UUID NOT NULL REFERENCES race_registration_fields(id) ON DELETE CASCADE,
    value TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(participant_id, field_id)
);

CREATE INDEX IF NOT EXISTS idx_participant_registration_answers_participant
    ON participant_registration_answers(participant_id);

-- Existing registrations on free races are treated as payment-waived so approval
-- rules do not block historical rows.
UPDATE participants p
SET payment_status = 'WAIVED'::payment_status
FROM races r
WHERE p.race_id = r.id
  AND p.payment_status = 'UNPAID'::payment_status
  AND (r.registration_fee IS NULL OR r.registration_fee = 0);
