-- SOS jobs for a channel the recipient cannot be reached on (no device, email, or phone) are
-- recorded as SKIPPED instead of being reported as dispatched.
ALTER TABLE emergency_notification_jobs DROP CONSTRAINT emergency_notification_jobs_status_check;

ALTER TABLE emergency_notification_jobs
    ADD CONSTRAINT emergency_notification_jobs_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'DISPATCHED', 'EXHAUSTED', 'SKIPPED'));
