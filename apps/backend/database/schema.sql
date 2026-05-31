-- Race Tracker Platform Database Schema
-- PostgreSQL 15+

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

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
                       finish_location_lat DECIMAL(10, 8),
                       finish_location_lng DECIMAL(11, 8),

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
CREATE TABLE gps_locations (
                               id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                               participant_id UUID REFERENCES participants(id) ON DELETE CASCADE,
                               race_id UUID REFERENCES races(id) ON DELETE CASCADE,
                               latitude DECIMAL(10, 8) NOT NULL,
                               longitude DECIMAL(11, 8) NOT NULL,
                               altitude DECIMAL(8, 2),
                               accuracy DECIMAL(6, 2),
                               speed DECIMAL(6, 2), -- km/h
                               heading DECIMAL(5, 2), -- degrees
                               battery_level INTEGER,
                               recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
                               received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Checkpoint Records
CREATE TYPE record_method AS ENUM ('RFID', 'QR_CODE', 'MANUAL', 'GPS_AUTO');

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

-- Checkpoint Records
CREATE INDEX idx_checkpoint_records_participant ON checkpoint_records(participant_id);
CREATE INDEX idx_checkpoint_records_checkpoint ON checkpoint_records(checkpoint_id);
CREATE INDEX idx_checkpoint_records_race ON checkpoint_records(race_id);

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

-- =============================================
-- SEED DATA (Optional)
-- =============================================

-- Insert Super Admin
-- Password: admin123 (bcrypt hash)
INSERT INTO users (email, password_hash, first_name, last_name, role, is_active, email_verified)
VALUES ('admin@racetracker.com', '$2a$10$15iYnBnQe.NOKNB/Ev2TseNkDgSPGeu63gAWPxunWkkQF3KHLCDxW', 'System', 'Admin', 'SUPER_ADMIN', true, true);
