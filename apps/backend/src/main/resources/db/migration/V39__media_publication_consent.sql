ALTER TABLE participants
    ADD COLUMN media_publication_consent_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN media_publication_consent_version VARCHAR(40);

ALTER TABLE race_media
    ADD COLUMN non_person_publication_attested_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN non_person_publication_attested_by UUID REFERENCES users(id),
    ADD COLUMN non_person_publication_reason TEXT;

CREATE INDEX idx_race_media_approved_public
    ON race_media (race_id, created_at DESC) WHERE status = 'APPROVED';
