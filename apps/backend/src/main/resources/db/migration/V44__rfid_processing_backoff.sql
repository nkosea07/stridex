-- Bounded retry for RFID events whose normalization throws. Failed events back off instead of
-- being republished every recovery cycle, and are marked ERROR after the attempt limit so they
-- cannot crowd newer reads out of the recovery window.
ALTER TABLE rfid_read_events
    ADD COLUMN processing_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_rfid_read_events_received_retry
    ON rfid_read_events (received_at)
    WHERE status = 'RECEIVED';
