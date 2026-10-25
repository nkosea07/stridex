ALTER TABLE emergency_alerts
    ADD COLUMN delivery_status VARCHAR(24) NOT NULL DEFAULT 'LEGACY_UNVERIFIED';

ALTER TABLE emergency_alerts
    ALTER COLUMN delivery_status SET DEFAULT 'QUEUED';

ALTER TABLE emergency_alerts
    ADD CONSTRAINT emergency_alerts_delivery_status_check
    CHECK (delivery_status IN ('QUEUED', 'DISPATCHED', 'ESCALATION_REQUIRED', 'LEGACY_UNVERIFIED'));

ALTER TABLE emergency_alerts
    ADD CONSTRAINT emergency_alerts_coordinate_pair_check
    CHECK ((latitude IS NULL) = (longitude IS NULL));

ALTER TABLE emergency_alerts
    ADD CONSTRAINT emergency_alerts_coordinate_range_check
    CHECK (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180 OR latitude IS NULL AND longitude IS NULL);

CREATE TABLE emergency_notification_jobs (
    id UUID PRIMARY KEY,
    alert_id UUID NOT NULL REFERENCES emergency_alerts(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    dispatched_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT emergency_notification_jobs_status_check CHECK (status IN ('PENDING', 'PROCESSING', 'DISPATCHED', 'EXHAUSTED')),
    CONSTRAINT emergency_notification_jobs_channel_check CHECK (channel IN ('IN_APP', 'PUSH', 'EMAIL', 'SMS', 'WHATSAPP')),
    CONSTRAINT emergency_notification_jobs_unique UNIQUE (alert_id, recipient_id, channel)
);

CREATE INDEX idx_emergency_notification_jobs_due ON emergency_notification_jobs(next_attempt_at, created_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_emergency_notification_jobs_lease ON emergency_notification_jobs(lease_until)
    WHERE status = 'PROCESSING';
CREATE INDEX idx_emergency_notification_jobs_alert ON emergency_notification_jobs(alert_id, status);
CREATE INDEX idx_emergency_alerts_escalation ON emergency_alerts(created_at)
    WHERE delivery_status = 'ESCALATION_REQUIRED';
