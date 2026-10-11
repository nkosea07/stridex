-- Phase 5 (2026-10-11): media derivatives for the storage abstraction.
-- Thumbnails and original dimensions let galleries serve lightweight previews.

ALTER TABLE race_media
    ADD COLUMN IF NOT EXISTS thumbnail_key TEXT;

ALTER TABLE race_media
    ADD COLUMN IF NOT EXISTS width INTEGER;

ALTER TABLE race_media
    ADD COLUMN IF NOT EXISTS height INTEGER;
