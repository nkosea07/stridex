CREATE TABLE payment_reversal_cases (
    payment_id UUID PRIMARY KEY REFERENCES payments(id),
    provider_reference VARCHAR(160) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_reference VARCHAR(160)
);

CREATE INDEX idx_payment_reversal_cases_open
    ON payment_reversal_cases (observed_at) WHERE resolved_at IS NULL;
