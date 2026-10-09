-- Race media gallery (photos/videos) with tagging and moderation.

CREATE TABLE IF NOT EXISTS race_media (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    participant_id UUID REFERENCES participants(id) ON DELETE SET NULL,
    media_type VARCHAR(20) NOT NULL DEFAULT 'IMAGE',
    storage_key TEXT NOT NULL,
    content_type VARCHAR(100),
    caption TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    uploaded_by UUID REFERENCES users(id),
    moderated_by UUID REFERENCES users(id),
    moderated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_race_media_race_status ON race_media(race_id, status);
CREATE INDEX IF NOT EXISTS idx_race_media_participant ON race_media(participant_id);
