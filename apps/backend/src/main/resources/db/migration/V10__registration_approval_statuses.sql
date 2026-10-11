-- Registration workflow statuses are added in their own migration because PostgreSQL
-- does not allow a newly added enum value to be used in the same transaction that adds it.
-- Do not reference these values in this file.
ALTER TYPE participant_status ADD VALUE IF NOT EXISTS 'PENDING_REVIEW';
ALTER TYPE participant_status ADD VALUE IF NOT EXISTS 'WAITLISTED';
ALTER TYPE participant_status ADD VALUE IF NOT EXISTS 'REJECTED';
