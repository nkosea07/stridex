-- RFID/bib ingest foundation migration.
-- Adds reader registry, raw read events, and indexes for bridge-device ingestion.

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

CREATE INDEX idx_rfid_readers_race ON rfid_readers(race_id);
CREATE INDEX idx_rfid_readers_checkpoint ON rfid_readers(checkpoint_id);
CREATE INDEX idx_rfid_readers_api_key_hash ON rfid_readers(api_key_hash);
CREATE INDEX idx_rfid_read_events_reader ON rfid_read_events(reader_id);
CREATE INDEX idx_rfid_read_events_race ON rfid_read_events(race_id);
CREATE INDEX idx_rfid_read_events_checkpoint ON rfid_read_events(checkpoint_id);
CREATE INDEX idx_rfid_read_events_source ON rfid_read_events(reader_id, source_event_id);
CREATE INDEX idx_participants_rfid_tag ON participants(rfid_tag);

CREATE TRIGGER update_rfid_readers_updated_at
    BEFORE UPDATE ON rfid_readers
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
