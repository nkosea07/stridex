CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(80),
    target_id VARCHAR(160),
    outcome VARCHAR(24) NOT NULL,
    request_id VARCHAR(100),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT audit_events_action_not_blank CHECK (length(trim(action)) > 0),
    CONSTRAINT audit_events_outcome_not_blank CHECK (length(trim(outcome)) > 0)
);

CREATE INDEX idx_audit_events_created_at ON audit_events (created_at DESC, id DESC);
CREATE INDEX idx_audit_events_action_created ON audit_events (action, created_at DESC, id DESC);
CREATE INDEX idx_audit_events_actor_created ON audit_events (actor_user_id, created_at DESC, id DESC);

CREATE OR REPLACE FUNCTION prevent_audit_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_events are append-only';
END;
$$;

CREATE TRIGGER trg_audit_events_append_only
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW EXECUTE FUNCTION prevent_audit_event_mutation();
