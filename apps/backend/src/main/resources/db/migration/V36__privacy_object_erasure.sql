CREATE INDEX idx_race_media_uploaded_by
    ON race_media (uploaded_by) WHERE uploaded_by IS NOT NULL;

CREATE INDEX idx_participants_user_id
    ON participants (user_id) WHERE user_id IS NOT NULL;

ALTER TABLE race_media ADD COLUMN storage_scope TEXT;

CREATE TABLE account_erasures (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    staged_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finalized_at TIMESTAMP WITH TIME ZONE,
    notification_email VARCHAR(255),
    notification_first_name VARCHAR(255),
    notification_lease_token UUID,
    notification_lease_until TIMESTAMP WITH TIME ZONE,
    notification_dispatched_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT account_erasures_notification_state CHECK (
        notification_dispatched_at IS NULL OR finalized_at IS NOT NULL
    )
);

CREATE INDEX idx_account_erasures_unfinalized
    ON account_erasures (staged_at, user_id) WHERE finalized_at IS NULL;

CREATE INDEX idx_account_erasures_notification_due
    ON account_erasures (finalized_at, user_id)
    WHERE finalized_at IS NOT NULL AND notification_dispatched_at IS NULL;

CREATE TABLE privacy_object_deletion_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES account_erasures(user_id),
    storage_key TEXT NOT NULL,
    storage_scope TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT privacy_object_deletion_jobs_unique_key UNIQUE (user_id, storage_key),
    CONSTRAINT privacy_object_deletion_jobs_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED')),
    CONSTRAINT privacy_object_deletion_jobs_attempts CHECK (attempts >= 0),
    CONSTRAINT privacy_object_deletion_jobs_processing_lease CHECK (
        (status = 'PROCESSING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'PROCESSING' AND lease_token IS NULL AND lease_until IS NULL)
    )
);

CREATE INDEX idx_privacy_object_deletion_jobs_pending
    ON privacy_object_deletion_jobs (next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_privacy_object_deletion_jobs_processing
    ON privacy_object_deletion_jobs (lease_until)
    WHERE status = 'PROCESSING';

CREATE INDEX idx_privacy_object_deletion_jobs_unfinished_user
    ON privacy_object_deletion_jobs (user_id)
    WHERE status <> 'COMPLETED';
