-- Language for emails and notifications sent to a user. NULL means the default (English).
-- Values are validated against racetracker.i18n.supported-locales in the application.

ALTER TABLE users ADD COLUMN preferred_locale VARCHAR(10);
ALTER TABLE users ADD CONSTRAINT chk_users_preferred_locale
    CHECK (preferred_locale IS NULL OR preferred_locale ~ '^[a-z]{2,3}(-[A-Z]{2})?$');

ALTER TABLE account_erasures ADD COLUMN notification_locale VARCHAR(10);
