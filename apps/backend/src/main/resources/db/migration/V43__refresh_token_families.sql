-- Groups rotated refresh tokens by device session so reuse of a rotated token can revoke
-- that session without signing the user out of their other devices. Existing tokens keep a
-- NULL family and are treated as single-token sessions.
ALTER TABLE auth_tokens ADD COLUMN family_id UUID;

CREATE INDEX idx_auth_tokens_family_active
    ON auth_tokens (family_id)
    WHERE family_id IS NOT NULL AND consumed_at IS NULL;
