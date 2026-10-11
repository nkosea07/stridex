-- Registration payments (Pesepay: EcoCash, cards, Zimswitch, ...).

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES participants(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    provider_ref VARCHAR(160),
    merchant_reference VARCHAR(160),
    amount NUMERIC(10, 2),
    currency VARCHAR(3),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    checkout_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_payments_provider_ref UNIQUE (provider, provider_ref)
);

CREATE INDEX IF NOT EXISTS idx_payments_participant ON payments(participant_id);
CREATE INDEX IF NOT EXISTS idx_payments_merchant_reference ON payments(merchant_reference);
