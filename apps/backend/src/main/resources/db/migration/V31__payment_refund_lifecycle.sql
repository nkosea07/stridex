-- Refund lifecycle: record attempts before the provider call so a crash mid-call is auditable
-- and retries are idempotent.

ALTER TABLE payment_refunds
    ADD COLUMN IF NOT EXISTS error_message TEXT;

ALTER TABLE payment_refunds
    ALTER COLUMN status SET DEFAULT 'PENDING';
