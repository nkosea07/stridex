-- Auth token refresh hardening migration
-- Applies the enum/value and token column changes introduced by refresh-token persistence.

ALTER TYPE auth_token_type ADD VALUE IF NOT EXISTS 'REFRESH';

ALTER TABLE auth_tokens
    ALTER COLUMN token TYPE VARCHAR(1024);
