-- Promo/discount codes and payment discount tracking.

CREATE TABLE IF NOT EXISTS promo_codes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    race_id UUID REFERENCES races(id) ON DELETE CASCADE,
    code VARCHAR(40) NOT NULL,
    discount_type VARCHAR(10) NOT NULL DEFAULT 'PERCENT',
    discount_value NUMERIC(10, 2) NOT NULL,
    max_uses INTEGER,
    used_count INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_promo_codes_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_promo_codes_race ON promo_codes(race_id);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS promo_code VARCHAR(40),
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(10, 2);
