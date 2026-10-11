-- GDPR: consent tracking and account erasure marker.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS data_processing_consent_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS marketing_consent BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
