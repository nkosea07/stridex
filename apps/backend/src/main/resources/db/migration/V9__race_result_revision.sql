CREATE TABLE race_result_revisions (
    race_id UUID PRIMARY KEY REFERENCES races(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0)
);

CREATE OR REPLACE FUNCTION bump_race_result_revision(target_race_id UUID)
RETURNS void
LANGUAGE sql
AS $$
    INSERT INTO race_result_revisions (race_id, revision)
    VALUES (target_race_id, 1)
    ON CONFLICT (race_id) DO UPDATE SET revision = race_result_revisions.revision + 1
$$;

CREATE OR REPLACE FUNCTION track_participant_result_changes()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM bump_race_result_revision(NEW.race_id);
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM bump_race_result_revision(OLD.race_id);
        RETURN OLD;
    END IF;

    IF OLD.race_id IS DISTINCT FROM NEW.race_id
       OR OLD.status IS DISTINCT FROM NEW.status
       OR OLD.category_id IS DISTINCT FROM NEW.category_id
       OR OLD.gender IS DISTINCT FROM NEW.gender
       OR OLD.bib_number IS DISTINCT FROM NEW.bib_number
       OR OLD.checked_in_at IS DISTINCT FROM NEW.checked_in_at
       OR OLD.registered_at IS DISTINCT FROM NEW.registered_at THEN
        PERFORM bump_race_result_revision(OLD.race_id);
        IF NEW.race_id IS DISTINCT FROM OLD.race_id THEN
            PERFORM bump_race_result_revision(NEW.race_id);
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_participant_result_revision
AFTER INSERT OR UPDATE OR DELETE ON participants
FOR EACH ROW EXECUTE FUNCTION track_participant_result_changes();

CREATE OR REPLACE FUNCTION track_checkpoint_result_changes()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM bump_race_result_revision(NEW.race_id);
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM bump_race_result_revision(OLD.race_id);
        RETURN OLD;
    END IF;

    IF OLD.race_id IS DISTINCT FROM NEW.race_id
       OR OLD.participant_id IS DISTINCT FROM NEW.participant_id
       OR OLD.checkpoint_id IS DISTINCT FROM NEW.checkpoint_id
       OR OLD.recorded_at IS DISTINCT FROM NEW.recorded_at
       OR OLD.record_method IS DISTINCT FROM NEW.record_method
       OR OLD.is_verified IS DISTINCT FROM NEW.is_verified THEN
        PERFORM bump_race_result_revision(OLD.race_id);
        IF NEW.race_id IS DISTINCT FROM OLD.race_id THEN
            PERFORM bump_race_result_revision(NEW.race_id);
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_checkpoint_result_revision
AFTER INSERT OR UPDATE OR DELETE ON checkpoint_records
FOR EACH ROW EXECUTE FUNCTION track_checkpoint_result_changes();
