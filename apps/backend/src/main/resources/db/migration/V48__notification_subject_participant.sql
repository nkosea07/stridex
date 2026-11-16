-- Links a notification to the participant it is about (for example a follower's "X finished"
-- alert) so erasing that athlete can remove the notifications naming them from other users'
-- inboxes. Existing rows are backfilled from the JSON data payload; only well-formed UUIDs are
-- extracted, so malformed payloads are left unlinked rather than failing the migration.
ALTER TABLE notifications ADD COLUMN subject_participant_id UUID;

UPDATE notifications
SET subject_participant_id = substring(
        data FROM '"participantId"\s*:\s*"([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"'
    )::uuid
WHERE data LIKE '%"participantId"%'
  AND substring(
        data FROM '"participantId"\s*:\s*"([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"'
    ) IS NOT NULL;

CREATE INDEX idx_notifications_subject_participant
    ON notifications (subject_participant_id)
    WHERE subject_participant_id IS NOT NULL;
