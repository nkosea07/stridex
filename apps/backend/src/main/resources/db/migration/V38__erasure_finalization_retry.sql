ALTER TABLE account_erasures
    ADD COLUMN finalization_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN finalization_next_attempt_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN finalization_error_code VARCHAR(60),
    ADD COLUMN legacy_scrubbed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE account_erasures
    ADD CONSTRAINT account_erasures_finalization_attempts_nonnegative CHECK (finalization_attempts >= 0);

CREATE INDEX idx_account_erasures_legacy_scrub
    ON account_erasures (finalized_at, user_id) WHERE finalized_at IS NOT NULL AND legacy_scrubbed_at IS NULL;
