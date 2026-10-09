-- Race Tracker Platform Database Schema
-- PostgreSQL 15+

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;

-- =============================================
-- USERS & AUTHENTICATION
-- =============================================

CREATE TYPE user_role AS ENUM ('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL', 'VOLUNTEER', 'ATHLETE', 'SPECTATOR');

ALTER TYPE user_role ADD VALUE IF NOT EXISTS 'OFFICIAL';
ALTER TYPE user_role ADD VALUE IF NOT EXISTS 'ATHLETE';
ALTER TYPE user_role ADD VALUE IF NOT EXISTS 'SPECTATOR';

CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                       email VARCHAR(255) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       first_name VARCHAR(100) NOT NULL,
                       last_name VARCHAR(100) NOT NULL,
                       role user_role NOT NULL DEFAULT 'ATHLETE',
                       phone VARCHAR(20),
                       is_active BOOLEAN DEFAULT true,
                       email_verified BOOLEAN DEFAULT false,
                       failed_login_attempts INTEGER DEFAULT 0,
                       last_failed_login_at TIMESTAMP WITH TIME ZONE,
                       lockout_until TIMESTAMP WITH TIME ZONE,
                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE refresh_tokens (
                                id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                                token VARCHAR(500) UNIQUE NOT NULL,
                                expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TYPE auth_token_type AS ENUM ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'REFRESH');

ALTER TYPE auth_token_type ADD VALUE IF NOT EXISTS 'REFRESH';

CREATE TABLE auth_tokens (
                             id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                             user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                             token VARCHAR(1024) UNIQUE NOT NULL,
                             token_type auth_token_type NOT NULL,
                             expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                             consumed_at TIMESTAMP WITH TIME ZONE,
                             created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- RACES
-- =============================================

CREATE TYPE race_status AS ENUM ('DRAFT', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');
CREATE TYPE race_type AS ENUM ('MARATHON', 'HALF_MARATHON', 'ULTRA_MARATHON', '10K', '5K', 'TRAIL', 'CYCLING', 'TRIATHLON', 'OTHER');

CREATE TABLE races (
                       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                       name VARCHAR(255) NOT NULL,
                       description TEXT,
                       race_type race_type NOT NULL,
                       status race_status DEFAULT 'DRAFT',

    -- Location
                       city VARCHAR(100),
                       country VARCHAR(100),
                       start_location_lat DECIMAL(10, 8),
                       start_location_lng DECIMAL(11, 8),
                       start_location_geog geography(Point, 4326),
                       finish_location_lat DECIMAL(10, 8),
                       finish_location_lng DECIMAL(11, 8),
                       finish_location_geog geography(Point, 4326),

    -- Timing
                       race_date DATE NOT NULL,
                       start_time TIME,
                       registration_opens TIMESTAMP WITH TIME ZONE,
                       registration_closes TIMESTAMP WITH TIME ZONE,

    -- Details
                       distance_km DECIMAL(6, 2),
                       max_participants INTEGER,
                       registration_fee DECIMAL(10, 2),
                       currency VARCHAR(3) DEFAULT 'USD',

    -- Media
                       banner_image_url TEXT,
                       logo_url TEXT,

    -- Settings
                       is_public_registration BOOLEAN DEFAULT true,
                       require_medical_clearance BOOLEAN DEFAULT false,

    -- Ownership
                       created_by UUID REFERENCES users(id),
                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Race categories (e.g., Elite, Amateur, Age Groups)
CREATE TABLE race_categories (
                                 id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                 race_id UUID REFERENCES races(id) ON DELETE CASCADE,
                                 name VARCHAR(100) NOT NULL,
                                 description TEXT,
                                 min_age INTEGER,
                                 max_age INTEGER,
                                 gender VARCHAR(10), -- 'MALE', 'FEMALE', 'ANY'
                                 bib_prefix VARCHAR(10),
                                 max_participants INTEGER,
                                 registration_fee DECIMAL(10, 2),
                                 created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- CHECKPOINTS
-- =============================================

CREATE TYPE checkpoint_type AS ENUM ('START', 'INTERMEDIATE', 'WATER_STATION', 'MEDICAL', 'FINISH');

CREATE TABLE checkpoints (
                             id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                             race_id UUID REFERENCES races(id) ON DELETE CASCADE,
                             name VARCHAR(100) NOT NULL,
                             checkpoint_type checkpoint_type NOT NULL,
                             distance_from_start_km DECIMAL(6, 2),
                             latitude DECIMAL(10, 8),
                             longitude DECIMAL(11, 8),
                             location_geog geography(Point, 4326),
                             order_index INTEGER NOT NULL,
                             cutoff_time INTERVAL, -- Time limit to reach this checkpoint
                             has_rfid_reader BOOLEAN DEFAULT false,
                             has_qr_scanner BOOLEAN DEFAULT false,
                             created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- PARTICIPANTS
-- =============================================

CREATE TYPE participant_status AS ENUM ('REGISTERED', 'CONFIRMED', 'CHECKED_IN', 'STARTED', 'DNF', 'DNS', 'DISQUALIFIED', 'FINISHED');
CREATE TYPE gender_type AS ENUM ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY');

CREATE TABLE participants (
                              id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                              race_id UUID REFERENCES races(id) ON DELETE CASCADE,
                              category_id UUID REFERENCES race_categories(id),
                              user_id UUID REFERENCES users(id), -- Optional, for registered users

    -- Personal Info
                              first_name VARCHAR(100) NOT NULL,
                              last_name VARCHAR(100) NOT NULL,
                              email VARCHAR(255) NOT NULL,
                              phone VARCHAR(20),
                              date_of_birth DATE,
                              gender gender_type,
                              nationality VARCHAR(100),

    -- Race Info
                              bib_number VARCHAR(20) UNIQUE,
                              rfid_tag VARCHAR(100) UNIQUE,
                              status participant_status DEFAULT 'REGISTERED',

    -- Emergency Contact
                              emergency_contact_name VARCHAR(200),
                              emergency_contact_phone VARCHAR(20),
                              emergency_contact_relation VARCHAR(50),

    -- Medical
                              blood_type VARCHAR(5),
                              medical_conditions TEXT,
                              allergies TEXT,

    -- Club/Team
                              club_name VARCHAR(200),
                              team_name VARCHAR(200),

    -- Registration
                              registration_source VARCHAR(50), -- 'ADMIN', 'PUBLIC_PORTAL', 'CSV_IMPORT'
                              registered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                              checked_in_at TIMESTAMP WITH TIME ZONE,

    -- Tracking
                              is_tracking_enabled BOOLEAN DEFAULT true,
                              device_token VARCHAR(500), -- For push notifications
                              total_distance DECIMAL(10, 3) DEFAULT 0,

                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- TRACKING & TIMING
-- =============================================

-- GPS Location History
-- Future partitioning path:
-- gps_locations should be converted to RANGE partitioning on recorded_at in a dedicated migration.
-- Do not rewrite the table in this schema foundation phase; keep JPA/API compatibility first.
CREATE TABLE gps_locations (
                               id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                               participant_id UUID REFERENCES participants(id) ON DELETE CASCADE,
                               race_id UUID REFERENCES races(id) ON DELETE CASCADE,
                               latitude DECIMAL(10, 8) NOT NULL,
                               longitude DECIMAL(11, 8) NOT NULL,
                               location_geog geography(Point, 4326),
                               altitude DECIMAL(8, 2),
                               accuracy DECIMAL(6, 2),
                               speed DECIMAL(6, 2), -- km/h
                               heading DECIMAL(5, 2), -- degrees
                               battery_level INTEGER,
                               recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
                               received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Spatial columns for existing deployments that already created the base tables.
ALTER TABLE races
    ADD COLUMN IF NOT EXISTS start_location_geog geography(Point, 4326),
    ADD COLUMN IF NOT EXISTS finish_location_geog geography(Point, 4326);

ALTER TABLE checkpoints
    ADD COLUMN IF NOT EXISTS location_geog geography(Point, 4326);

ALTER TABLE gps_locations
    ADD COLUMN IF NOT EXISTS location_geog geography(Point, 4326);

-- Backfill spatial points from compatibility latitude/longitude columns.
UPDATE races
SET start_location_geog = ST_SetSRID(ST_MakePoint(start_location_lng::double precision, start_location_lat::double precision), 4326)::geography
WHERE start_location_lat IS NOT NULL
  AND start_location_lng IS NOT NULL
  AND start_location_geog IS NULL;

UPDATE races
SET finish_location_geog = ST_SetSRID(ST_MakePoint(finish_location_lng::double precision, finish_location_lat::double precision), 4326)::geography
WHERE finish_location_lat IS NOT NULL
  AND finish_location_lng IS NOT NULL
  AND finish_location_geog IS NULL;

UPDATE checkpoints
SET location_geog = ST_SetSRID(ST_MakePoint(longitude::double precision, latitude::double precision), 4326)::geography
WHERE latitude IS NOT NULL
  AND longitude IS NOT NULL
  AND location_geog IS NULL;

UPDATE gps_locations
SET location_geog = ST_SetSRID(ST_MakePoint(longitude::double precision, latitude::double precision), 4326)::geography
WHERE location_geog IS NULL;

-- Keep spatial query columns in sync with the decimal latitude/longitude source fields.
CREATE OR REPLACE FUNCTION set_race_location_geog()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.start_location_lat IS NOT NULL AND NEW.start_location_lng IS NOT NULL THEN
        NEW.start_location_geog = ST_SetSRID(ST_MakePoint(NEW.start_location_lng::double precision, NEW.start_location_lat::double precision), 4326)::geography;
    ELSE
        NEW.start_location_geog = NULL;
    END IF;

    IF NEW.finish_location_lat IS NOT NULL AND NEW.finish_location_lng IS NOT NULL THEN
        NEW.finish_location_geog = ST_SetSRID(ST_MakePoint(NEW.finish_location_lng::double precision, NEW.finish_location_lat::double precision), 4326)::geography;
    ELSE
        NEW.finish_location_geog = NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS set_race_location_geog_before_write ON races;
CREATE TRIGGER set_race_location_geog_before_write
    BEFORE INSERT OR UPDATE OF start_location_lat, start_location_lng, finish_location_lat, finish_location_lng
    ON races
    FOR EACH ROW
    EXECUTE FUNCTION set_race_location_geog();

CREATE OR REPLACE FUNCTION set_checkpoint_location_geog()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        NEW.location_geog = ST_SetSRID(ST_MakePoint(NEW.longitude::double precision, NEW.latitude::double precision), 4326)::geography;
    ELSE
        NEW.location_geog = NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS set_checkpoint_location_geog_before_write ON checkpoints;
CREATE TRIGGER set_checkpoint_location_geog_before_write
    BEFORE INSERT OR UPDATE OF latitude, longitude
    ON checkpoints
    FOR EACH ROW
    EXECUTE FUNCTION set_checkpoint_location_geog();

CREATE OR REPLACE FUNCTION set_gps_location_geog()
RETURNS TRIGGER AS $$
BEGIN
    NEW.location_geog = ST_SetSRID(ST_MakePoint(NEW.longitude::double precision, NEW.latitude::double precision), 4326)::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS set_gps_location_geog_before_write ON gps_locations;
CREATE TRIGGER set_gps_location_geog_before_write
    BEFORE INSERT OR UPDATE OF latitude, longitude
    ON gps_locations
    FOR EACH ROW
    EXECUTE FUNCTION set_gps_location_geog();

-- Checkpoint Records
CREATE TYPE record_method AS ENUM ('RFID', 'QR_CODE', 'MANUAL', 'GPS_AUTO');
CREATE TYPE rfid_reader_status AS ENUM ('ACTIVE', 'REVOKED');
CREATE TYPE rfid_read_event_status AS ENUM ('ACCEPTED', 'DUPLICATE', 'UNKNOWN_TAG', 'INVALID_READER', 'ERROR');

CREATE TABLE rfid_readers (
                              id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                              race_id UUID REFERENCES races(id) ON DELETE CASCADE NOT NULL,
                              checkpoint_id UUID REFERENCES checkpoints(id) ON DELETE CASCADE NOT NULL,
                              name VARCHAR(200) NOT NULL,
                              external_id VARCHAR(200) NOT NULL,
                              api_key_hash VARCHAR(64) UNIQUE NOT NULL,
                              status rfid_reader_status NOT NULL DEFAULT 'ACTIVE',
                              last_seen_at TIMESTAMP WITH TIME ZONE,
                              last_ip VARCHAR(64),
                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                              UNIQUE(race_id, external_id)
);

CREATE TABLE checkpoint_records (
                                    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                    participant_id UUID REFERENCES participants(id) ON DELETE CASCADE,
                                    checkpoint_id UUID REFERENCES checkpoints(id) ON DELETE CASCADE,
                                    race_id UUID REFERENCES races(id) ON DELETE CASCADE,

                                    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                    record_method record_method NOT NULL,
                                    recorded_by UUID REFERENCES users(id), -- For manual entries

    -- Calculated times
                                    split_time INTERVAL, -- Time from previous checkpoint
                                    elapsed_time INTERVAL, -- Time from race start

    -- Verification
                                    is_verified BOOLEAN DEFAULT false,
                                    notes TEXT,

                                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

                                    UNIQUE(participant_id, checkpoint_id)
);

CREATE TABLE rfid_read_events (
                                  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                  reader_id UUID REFERENCES rfid_readers(id) ON DELETE SET NULL,
                                  race_id UUID REFERENCES races(id) ON DELETE CASCADE NOT NULL,
                                  checkpoint_id UUID REFERENCES checkpoints(id) ON DELETE CASCADE NOT NULL,
                                  rfid_tag VARCHAR(100) NOT NULL,
                                  source_event_id VARCHAR(200),
                                  read_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                  received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                  antenna VARCHAR(100),
                                  rssi DECIMAL(8, 2),
                                  raw_payload TEXT,
                                  status rfid_read_event_status NOT NULL,
                                  matched_participant_id UUID REFERENCES participants(id) ON DELETE SET NULL,
                                  checkpoint_record_id UUID REFERENCES checkpoint_records(id) ON DELETE SET NULL,
                                  error_message TEXT
);

-- Race Results
CREATE TABLE race_results (
                              id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                              participant_id UUID REFERENCES participants(id) ON DELETE CASCADE,
                              race_id UUID REFERENCES races(id) ON DELETE CASCADE,
                              category_id UUID REFERENCES race_categories(id),

    -- Times
                              gun_time INTERVAL, -- Time from official start
                              chip_time INTERVAL, -- Time from crossing start line

    -- Positions
                              overall_position INTEGER,
                              gender_position INTEGER,
                              category_position INTEGER,

    -- Performance
                              average_pace INTERVAL, -- per km
                              average_speed DECIMAL(5, 2), -- km/h

    -- Status
                              is_official BOOLEAN DEFAULT false,
                              is_disqualified BOOLEAN DEFAULT false,
                              disqualification_reason TEXT,

                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

                              UNIQUE(participant_id, race_id)
);

-- =============================================
-- RACE MANAGEMENT
-- =============================================

-- Race Admins (many-to-many)
CREATE TABLE race_admins (
                             race_id UUID REFERENCES races(id) ON DELETE CASCADE,
                             user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                             assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                             assigned_by UUID REFERENCES users(id),
                             PRIMARY KEY (race_id, user_id)
);

-- Volunteers assigned to checkpoints
CREATE TABLE checkpoint_volunteers (
                                       checkpoint_id UUID REFERENCES checkpoints(id) ON DELETE CASCADE,
                                       user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                                       assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                       PRIMARY KEY (checkpoint_id, user_id)
);

-- =============================================
-- NOTIFICATIONS & EMAILS
-- =============================================

CREATE TYPE notification_type AS ENUM ('REGISTRATION_CONFIRMATION', 'RACE_REMINDER', 'RACE_UPDATE', 'CHECKPOINT_NOTIFICATION', 'RESULT_AVAILABLE');

CREATE TABLE notifications (
                               id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                               user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                               title VARCHAR(255) NOT NULL,
                               message TEXT NOT NULL,
                               type VARCHAR(50) NOT NULL,
                               is_read BOOLEAN DEFAULT false,
                               read_at TIMESTAMP WITH TIME ZONE,
                               data TEXT,
                               created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE email_templates (
                                 id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                 race_id UUID REFERENCES races(id) ON DELETE CASCADE,
                                 notification_type notification_type NOT NULL,
                                 subject VARCHAR(255) NOT NULL,
                                 body_html TEXT NOT NULL,
                                 body_text TEXT,
                                 is_active BOOLEAN DEFAULT true,
                                 created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE email_logs (
                            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                            participant_id UUID REFERENCES participants(id) ON DELETE SET NULL,
                            template_id UUID REFERENCES email_templates(id) ON DELETE SET NULL,
                            recipient_email VARCHAR(255) NOT NULL,
                            subject VARCHAR(255) NOT NULL,
                            status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, SENT, FAILED
                            sent_at TIMESTAMP WITH TIME ZONE,
                            error_message TEXT,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- INDEXES
-- =============================================

-- Users
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_auth_tokens_user ON auth_tokens(user_id);
CREATE INDEX idx_auth_tokens_type ON auth_tokens(token_type);

-- Races
CREATE INDEX idx_races_status ON races(status);
CREATE INDEX idx_races_date ON races(race_date);
CREATE INDEX idx_races_created_by ON races(created_by);
CREATE INDEX IF NOT EXISTS idx_races_start_location_geog ON races USING GIST (start_location_geog);
CREATE INDEX IF NOT EXISTS idx_races_finish_location_geog ON races USING GIST (finish_location_geog);

-- Participants
CREATE INDEX idx_participants_race ON participants(race_id);
CREATE INDEX idx_participants_bib ON participants(bib_number);
CREATE INDEX idx_participants_status ON participants(status);
CREATE INDEX idx_participants_email ON participants(email);

-- GPS Locations
CREATE INDEX idx_gps_participant ON gps_locations(participant_id);
CREATE INDEX idx_gps_race ON gps_locations(race_id);
CREATE INDEX idx_gps_recorded_at ON gps_locations(recorded_at);
CREATE INDEX idx_gps_location ON gps_locations(latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_gps_locations_location_geog ON gps_locations USING GIST (location_geog);

-- Checkpoints
CREATE INDEX IF NOT EXISTS idx_checkpoints_location_geog ON checkpoints USING GIST (location_geog);

-- Checkpoint Records
CREATE INDEX idx_checkpoint_records_participant ON checkpoint_records(participant_id);
CREATE INDEX idx_checkpoint_records_checkpoint ON checkpoint_records(checkpoint_id);
CREATE INDEX idx_checkpoint_records_race ON checkpoint_records(race_id);
CREATE INDEX idx_rfid_readers_race ON rfid_readers(race_id);
CREATE INDEX idx_rfid_readers_checkpoint ON rfid_readers(checkpoint_id);
CREATE INDEX idx_rfid_readers_api_key_hash ON rfid_readers(api_key_hash);
CREATE INDEX idx_rfid_read_events_reader ON rfid_read_events(reader_id);
CREATE INDEX idx_rfid_read_events_race ON rfid_read_events(race_id);
CREATE INDEX idx_rfid_read_events_checkpoint ON rfid_read_events(checkpoint_id);
CREATE INDEX idx_rfid_read_events_source ON rfid_read_events(reader_id, source_event_id);
CREATE INDEX idx_participants_rfid_tag ON participants(rfid_tag);

-- Notifications
CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);
CREATE INDEX idx_notifications_user_title ON notifications(user_id, title);

-- Results
CREATE INDEX idx_results_race ON race_results(race_id);
CREATE INDEX idx_results_overall ON race_results(race_id, overall_position);

-- =============================================
-- TRIGGERS
-- =============================================

-- Update timestamp trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply to tables
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_races_updated_at BEFORE UPDATE ON races FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_participants_updated_at BEFORE UPDATE ON participants FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_race_results_updated_at BEFORE UPDATE ON race_results FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_email_templates_updated_at BEFORE UPDATE ON email_templates FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_rfid_readers_updated_at BEFORE UPDATE ON rfid_readers FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
