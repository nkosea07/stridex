-- Records which version of the Terms and Conditions each user accepted and when, alongside the
-- existing privacy policy acceptance (V32). NULL means the user has not accepted any version.
ALTER TABLE users
    ADD COLUMN terms_version VARCHAR(40),
    ADD COLUMN terms_accepted_at TIMESTAMP WITH TIME ZONE;
