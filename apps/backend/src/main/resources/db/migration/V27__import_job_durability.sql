-- Import job durability: store the uploaded file path and attempt count so a worker can
-- resume or retry a job after a restart instead of relying on fire-and-forget @Async.

ALTER TABLE import_jobs
    ADD COLUMN IF NOT EXISTS source_path TEXT;

ALTER TABLE import_jobs
    ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_import_jobs_status_updated ON import_jobs(status, updated_at);
