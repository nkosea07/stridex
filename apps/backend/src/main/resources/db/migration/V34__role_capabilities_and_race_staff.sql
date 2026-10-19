-- Normal user experiences are independent from the legacy role column.  Roles remain
-- intact for compatibility with existing callers and privileged authorization rules.

CREATE TYPE user_capability AS ENUM ('ATHLETE', 'SPECTATOR');

ALTER TABLE users
    ADD COLUMN preferred_capability user_capability;

CREATE TABLE user_capabilities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    capability user_capability NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_user_capabilities_user_capability UNIQUE (user_id, capability),
    CONSTRAINT ck_user_capabilities_active_revocation
        CHECK ((active = true AND revoked_at IS NULL) OR (active = false AND revoked_at IS NOT NULL))
);

CREATE INDEX idx_user_capabilities_active_user
    ON user_capabilities (user_id, capability)
    WHERE active = true;

CREATE TABLE race_staff_assignments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    staff_role user_role NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID REFERENCES users(id),
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by UUID REFERENCES users(id),
    CONSTRAINT ck_race_staff_assignments_role CHECK (staff_role IN ('OFFICIAL', 'VOLUNTEER')),
    CONSTRAINT ck_race_staff_assignments_active_revocation
        CHECK ((active = true AND revoked_at IS NULL AND revoked_by IS NULL)
            OR (active = false AND revoked_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_race_staff_assignments_active_user
    ON race_staff_assignments (race_id, user_id)
    WHERE active = true;

CREATE INDEX idx_race_staff_assignments_user_active
    ON race_staff_assignments (user_id, active);

CREATE INDEX idx_race_staff_assignments_race_active_role
    ON race_staff_assignments (race_id, active, staff_role);

-- Backfill normal capabilities only for normal legacy roles.  A privileged role that
-- happens to have follows remains capability-free until the user explicitly opts in.
INSERT INTO user_capabilities (user_id, capability)
SELECT u.id, 'ATHLETE'::user_capability
FROM users u
WHERE u.role = 'ATHLETE'::user_role
ON CONFLICT (user_id, capability) DO NOTHING;

INSERT INTO user_capabilities (user_id, capability)
SELECT u.id, 'SPECTATOR'::user_capability
FROM users u
WHERE u.role = 'SPECTATOR'::user_role
   OR (u.role IN ('ATHLETE'::user_role, 'SPECTATOR'::user_role) AND EXISTS (
       SELECT 1 FROM athlete_follows af WHERE af.user_id = u.id
   ))
ON CONFLICT (user_id, capability) DO NOTHING;

UPDATE users u
SET preferred_capability = CASE
    WHEN u.role = 'SPECTATOR'::user_role
      OR (u.role IN ('ATHLETE'::user_role, 'SPECTATOR'::user_role) AND EXISTS (
          SELECT 1 FROM athlete_follows af WHERE af.user_id = u.id
      ))
        THEN 'SPECTATOR'::user_capability
    WHEN u.role = 'ATHLETE'::user_role THEN 'ATHLETE'::user_capability
    ELSE NULL
END
WHERE u.preferred_capability IS NULL;
-- Checkpoint volunteers remain checkpoint-scoped through checkpoint_volunteers.
-- There is no explicit race-wide staff source in the legacy schema, so this
-- migration intentionally does not backfill race_staff_assignments for them.
