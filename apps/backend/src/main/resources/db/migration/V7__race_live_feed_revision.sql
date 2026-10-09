CREATE TABLE race_feed_revisions (
    race_id UUID PRIMARY KEY REFERENCES races(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0)
);

CREATE OR REPLACE FUNCTION bump_race_feed_revision(target_race_id UUID)
RETURNS void
LANGUAGE sql
AS $$
    INSERT INTO race_feed_revisions (race_id, revision)
    VALUES (target_race_id, 1)
    ON CONFLICT (race_id) DO UPDATE SET revision = race_feed_revisions.revision + 1
$$;

CREATE OR REPLACE FUNCTION track_live_feed_participant_changes()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status = 'STARTED' AND EXISTS (
            SELECT 1 FROM gps_locations g WHERE g.race_id = NEW.race_id AND g.participant_id = NEW.id
        ) THEN
            PERFORM bump_race_feed_revision(NEW.race_id);
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        IF OLD.status = 'STARTED' AND EXISTS (
            SELECT 1 FROM gps_locations g WHERE g.race_id = OLD.race_id AND g.participant_id = OLD.id
        ) THEN
            PERFORM bump_race_feed_revision(OLD.race_id);
        END IF;
        RETURN OLD;
    END IF;

    IF (OLD.race_id IS DISTINCT FROM NEW.race_id
        OR OLD.status IS DISTINCT FROM NEW.status
        OR OLD.bib_number IS DISTINCT FROM NEW.bib_number
        OR OLD.registered_at IS DISTINCT FROM NEW.registered_at)
       AND ((OLD.status = 'STARTED' AND EXISTS (
                SELECT 1 FROM gps_locations g WHERE g.race_id = OLD.race_id AND g.participant_id = OLD.id
            )) OR (NEW.status = 'STARTED' AND EXISTS (
                SELECT 1 FROM gps_locations g WHERE g.race_id = NEW.race_id AND g.participant_id = NEW.id
            ))) THEN
        PERFORM bump_race_feed_revision(OLD.race_id);
        IF NEW.race_id IS DISTINCT FROM OLD.race_id THEN
            PERFORM bump_race_feed_revision(NEW.race_id);
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_live_feed_participant_revision
AFTER INSERT OR UPDATE OR DELETE ON participants
FOR EACH ROW EXECUTE FUNCTION track_live_feed_participant_changes();

CREATE OR REPLACE FUNCTION track_live_feed_first_last_location()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    target_race_id UUID;
    target_participant_id UUID;
    target_location_id UUID;
BEGIN
    IF TG_OP = 'INSERT' THEN
        target_race_id := NEW.race_id;
        target_participant_id := NEW.participant_id;
        target_location_id := NEW.id;
    ELSE
        target_race_id := OLD.race_id;
        target_participant_id := OLD.participant_id;
        target_location_id := OLD.id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM participants p
        WHERE p.id = target_participant_id AND p.race_id = target_race_id AND p.status = 'STARTED'
    ) AND NOT EXISTS (
        SELECT 1 FROM gps_locations g
        WHERE g.race_id = target_race_id AND g.participant_id = target_participant_id AND g.id <> target_location_id
    ) THEN
        PERFORM bump_race_feed_revision(target_race_id);
    END IF;

    IF TG_OP = 'INSERT' THEN RETURN NEW; ELSE RETURN OLD; END IF;
END;
$$;

CREATE TRIGGER trg_live_feed_location_revision
AFTER INSERT OR DELETE ON gps_locations
FOR EACH ROW EXECUTE FUNCTION track_live_feed_first_last_location();
