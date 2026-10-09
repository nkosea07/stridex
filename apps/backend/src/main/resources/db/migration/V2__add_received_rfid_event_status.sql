-- Add the queued/received state used by the Redis Stream processing pipeline.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_enum
        WHERE enumlabel = 'RECEIVED'
          AND enumtypid = 'rfid_read_event_status'::regtype
    ) THEN
        ALTER TYPE rfid_read_event_status ADD VALUE 'RECEIVED' BEFORE 'ACCEPTED';
    END IF;
END
$$;
