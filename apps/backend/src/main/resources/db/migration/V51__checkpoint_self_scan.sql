-- Athlete self-scan integrity: a per-checkpoint secret printed in the self-scan QR code, and
-- provisional claims for scans that GPS could not confirm. Claims never affect results until an
-- official approves them.

ALTER TABLE checkpoints ADD COLUMN self_scan_token VARCHAR(64);

UPDATE checkpoints
SET self_scan_token = replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '')
WHERE self_scan_token IS NULL;

ALTER TABLE checkpoints ALTER COLUMN self_scan_token SET NOT NULL;
ALTER TABLE checkpoints ALTER COLUMN self_scan_token
    SET DEFAULT replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '');

CREATE TABLE self_scan_claims (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    checkpoint_id UUID NOT NULL REFERENCES checkpoints(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES participants(id) ON DELETE CASCADE,
    claimed_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(40) NOT NULL,
    distance_meters NUMERIC(10, 1),
    fix_age_seconds INTEGER,
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    review_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_self_scan_claims_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUPERSEDED')),
    CONSTRAINT chk_self_scan_claims_reason CHECK (reason IN ('NO_RECENT_FIX', 'OUT_OF_RANGE', 'CHECKPOINT_NOT_LOCATED'))
);

CREATE UNIQUE INDEX uq_self_scan_claims_pending
    ON self_scan_claims (participant_id, checkpoint_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_self_scan_claims_race_status_claimed
    ON self_scan_claims (race_id, status, claimed_at);

CREATE INDEX idx_self_scan_claims_checkpoint ON self_scan_claims (checkpoint_id);
CREATE INDEX idx_self_scan_claims_reviewed_by ON self_scan_claims (reviewed_by) WHERE reviewed_by IS NOT NULL;
