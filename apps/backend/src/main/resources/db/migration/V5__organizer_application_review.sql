ALTER TABLE organizer_applications
    ADD COLUMN reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE;
