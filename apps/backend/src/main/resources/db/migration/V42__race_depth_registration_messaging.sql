ALTER TABLE races
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    ADD COLUMN poster_url TEXT,
    ADD COLUMN cancellation_reason TEXT,
    ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN early_bird_ends_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN early_bird_fee DECIMAL(10, 2),
    ADD COLUMN team_fee DECIMAL(10, 2),
    ADD COLUMN bib_prefix VARCHAR(12),
    ADD COLUMN next_bib_sequence INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN bib_width INTEGER NOT NULL DEFAULT 4,
    ADD COLUMN medal_places INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN bib_template VARCHAR(40) NOT NULL DEFAULT 'CLASSIC',
    ADD COLUMN bib_accent_color VARCHAR(12);

ALTER TABLE races
    ADD CONSTRAINT races_next_bib_sequence_positive CHECK (next_bib_sequence > 0),
    ADD CONSTRAINT races_bib_width_valid CHECK (bib_width BETWEEN 1 AND 10),
    ADD CONSTRAINT races_medal_places_valid CHECK (medal_places BETWEEN 0 AND 100);

ALTER TABLE race_categories
    ADD COLUMN early_bird_fee DECIMAL(10, 2),
    ADD COLUMN team_fee DECIMAL(10, 2);

ALTER TABLE participants
    ADD COLUMN athlete_code VARCHAR(24);

UPDATE participants
SET athlete_code = 'ATH-' || UPPER(SUBSTRING(REPLACE(id::text, '-', '') FROM 1 FOR 12))
WHERE athlete_code IS NULL;

ALTER TABLE participants
    ALTER COLUMN athlete_code SET NOT NULL;

CREATE UNIQUE INDEX uq_participants_athlete_code ON participants (athlete_code);

CREATE TABLE race_stages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    sequence INTEGER NOT NULL,
    stage_date DATE,
    start_time TIME,
    distance_km DECIMAL(6, 2),
    route_description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_race_stages_sequence UNIQUE (race_id, sequence),
    CONSTRAINT race_stages_sequence_positive CHECK (sequence > 0)
);

CREATE INDEX idx_race_stages_race ON race_stages (race_id, sequence);

ALTER TABLE checkpoints
    ADD COLUMN stage_id UUID REFERENCES race_stages(id) ON DELETE SET NULL;

CREATE INDEX idx_checkpoints_stage ON checkpoints (stage_id);

CREATE TABLE race_sponsors (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    logo_url TEXT,
    website_url TEXT,
    tier VARCHAR(40),
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_race_sponsors_race ON race_sponsors (race_id, display_order);

ALTER TABLE notification_preferences
    ALTER COLUMN sms_enabled SET DEFAULT false;

UPDATE notification_preferences SET sms_enabled = false;

ALTER TABLE notification_templates
    ADD COLUMN meta_template_name VARCHAR(160),
    ADD COLUMN language_code VARCHAR(20) DEFAULT 'en_US';

ALTER TABLE notification_deliveries
    ADD COLUMN provider_message_id VARCHAR(200),
    ADD COLUMN provider_status VARCHAR(40),
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_notification_deliveries_provider_message
    ON notification_deliveries (provider_message_id)
    WHERE provider_message_id IS NOT NULL;
