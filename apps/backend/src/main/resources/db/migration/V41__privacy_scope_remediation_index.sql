CREATE INDEX idx_privacy_object_deletion_jobs_scope
    ON privacy_object_deletion_jobs (storage_scope)
    WHERE status IN ('PENDING', 'PROCESSING');
