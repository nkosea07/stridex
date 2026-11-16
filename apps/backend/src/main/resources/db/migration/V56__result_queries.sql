-- Athlete result queries: an athlete asks officials to review their time; officials resolve or
-- reject it with a recorded note. Results cannot be certified while queries are open.

CREATE TABLE result_queries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES participants(id) ON DELETE CASCADE,
    submitted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    checkpoint_id UUID REFERENCES checkpoints(id) ON DELETE SET NULL,
    message VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolution_note VARCHAR(1000),
    resolved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_result_queries_status CHECK (status IN ('OPEN', 'RESOLVED', 'REJECTED'))
);

CREATE INDEX idx_result_queries_race_status ON result_queries (race_id, status, created_at);
CREATE INDEX idx_result_queries_participant ON result_queries (participant_id);
CREATE INDEX idx_result_queries_submitted_by ON result_queries (submitted_by) WHERE submitted_by IS NOT NULL;
CREATE INDEX idx_result_queries_checkpoint ON result_queries (checkpoint_id) WHERE checkpoint_id IS NOT NULL;
CREATE INDEX idx_result_queries_resolved_by ON result_queries (resolved_by) WHERE resolved_by IS NOT NULL;
