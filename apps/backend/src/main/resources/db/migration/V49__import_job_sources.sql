-- Import uploads are stored with their job instead of on one instance's local disk, so any
-- backend instance can process or resume them. The row is removed when the job finishes.
-- Jobs created before this migration keep their local source_path.
CREATE TABLE import_job_sources (
    job_id UUID PRIMARY KEY REFERENCES import_jobs(id) ON DELETE CASCADE,
    content BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
