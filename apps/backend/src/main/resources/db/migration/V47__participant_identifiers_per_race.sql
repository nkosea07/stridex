-- Bib numbers and legacy RFID tags identify a participant within one race. The baseline made
-- them unique across every race, so one organizer's numbering (or a reused timing chip) blocked
-- every other race. Replace the global constraints with per-race unique indexes; existing data
-- already satisfies the narrower rule.
DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
        WHERE con.conrelid = 'participants'::regclass
          AND con.contype = 'u'
          AND cardinality(con.conkey) = 1
          AND att.attname IN ('bib_number', 'rfid_tag')
    LOOP
        EXECUTE format('ALTER TABLE participants DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

CREATE UNIQUE INDEX uq_participants_race_bib_number
    ON participants (race_id, bib_number)
    WHERE bib_number IS NOT NULL;

CREATE UNIQUE INDEX uq_participants_race_rfid_tag
    ON participants (race_id, rfid_tag)
    WHERE rfid_tag IS NOT NULL;
