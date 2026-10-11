-- Privacy policy acceptance recorded at registration (versioned policy).

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS privacy_policy_version VARCHAR(40);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS privacy_policy_accepted_at TIMESTAMP WITH TIME ZONE;
