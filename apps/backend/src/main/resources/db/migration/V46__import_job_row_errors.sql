-- Row-level reasons for skipped import rows (JSON array of {"row": n, "reason": "..."}),
-- capped by the application so a large bad file cannot bloat the job row.
ALTER TABLE import_jobs ADD COLUMN row_errors TEXT;
