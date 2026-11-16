-- Result integrity: versioned, hash-chained result releases; certification lock; append-only
-- correction history for checkpoint times.

ALTER TABLE races ADD COLUMN results_certified_at TIMESTAMPTZ;
ALTER TABLE races ADD COLUMN results_certified_by UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE race_result_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_count INTEGER NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    previous_chain_sha256 VARCHAR(64),
    chain_sha256 VARCHAR(64) NOT NULL,
    snapshot JSONB NOT NULL,
    published_by UUID REFERENCES users(id) ON DELETE SET NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    certified_by UUID REFERENCES users(id) ON DELETE SET NULL,
    certified_at TIMESTAMPTZ,
    CONSTRAINT uq_race_result_versions_version UNIQUE (race_id, version),
    CONSTRAINT chk_race_result_versions_status CHECK (status IN ('PROVISIONAL', 'OFFICIAL', 'SUPERSEDED'))
);

CREATE INDEX idx_race_result_versions_published_by ON race_result_versions (published_by) WHERE published_by IS NOT NULL;
CREATE INDEX idx_race_result_versions_certified_by ON race_result_versions (certified_by) WHERE certified_by IS NOT NULL;

-- A published version is evidence: its content, hashes, and publisher never change and it is
-- never deleted (except when the whole race is deleted). Only the status and certification fields
-- move forward.
CREATE OR REPLACE FUNCTION protect_race_result_versions()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF EXISTS (SELECT 1 FROM races WHERE id = OLD.race_id) THEN
            RAISE EXCEPTION 'race_result_versions are append-only';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.race_id IS DISTINCT FROM OLD.race_id
        OR NEW.version IS DISTINCT FROM OLD.version
        OR NEW.result_count IS DISTINCT FROM OLD.result_count
        OR NEW.content_sha256 IS DISTINCT FROM OLD.content_sha256
        OR NEW.previous_chain_sha256 IS DISTINCT FROM OLD.previous_chain_sha256
        OR NEW.chain_sha256 IS DISTINCT FROM OLD.chain_sha256
        OR NEW.snapshot IS DISTINCT FROM OLD.snapshot
        OR NEW.published_at IS DISTINCT FROM OLD.published_at THEN
        RAISE EXCEPTION 'published result versions cannot be modified';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_race_result_versions_protect
BEFORE UPDATE OR DELETE ON race_result_versions
FOR EACH ROW EXECUTE FUNCTION protect_race_result_versions();

CREATE TABLE checkpoint_record_corrections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checkpoint_record_id UUID NOT NULL REFERENCES checkpoint_records(id) ON DELETE CASCADE,
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES participants(id) ON DELETE CASCADE,
    checkpoint_id UUID NOT NULL REFERENCES checkpoints(id) ON DELETE CASCADE,
    previous_effective_at TIMESTAMPTZ,
    new_adjusted_at TIMESTAMPTZ,
    previous_verified BOOLEAN,
    new_verified BOOLEAN,
    reason VARCHAR(500) NOT NULL,
    corrected_by UUID REFERENCES users(id) ON DELETE SET NULL,
    corrected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_checkpoint_record_corrections_record ON checkpoint_record_corrections (checkpoint_record_id, corrected_at);
CREATE INDEX idx_checkpoint_record_corrections_race ON checkpoint_record_corrections (race_id, corrected_at);
CREATE INDEX idx_checkpoint_record_corrections_participant ON checkpoint_record_corrections (participant_id);
CREATE INDEX idx_checkpoint_record_corrections_checkpoint ON checkpoint_record_corrections (checkpoint_id);
CREATE INDEX idx_checkpoint_record_corrections_corrected_by ON checkpoint_record_corrections (corrected_by) WHERE corrected_by IS NOT NULL;

CREATE OR REPLACE FUNCTION protect_checkpoint_record_corrections()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND NOT EXISTS (SELECT 1 FROM checkpoint_records WHERE id = OLD.checkpoint_record_id) THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'checkpoint_record_corrections are append-only';
END;
$$;

CREATE TRIGGER trg_checkpoint_record_corrections_protect
BEFORE UPDATE OR DELETE ON checkpoint_record_corrections
FOR EACH ROW EXECUTE FUNCTION protect_checkpoint_record_corrections();

-- Reason recorded with an official status decision (DQ, DNF, DNS) made by staff.
ALTER TABLE participants ADD COLUMN status_reason VARCHAR(500);
