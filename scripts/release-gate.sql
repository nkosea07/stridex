\set ON_ERROR_STOP on

DO $$
DECLARE
    blocked INTEGER;
BEGIN
    SELECT count(*) INTO blocked
    FROM payment_refunds r
    JOIN payments p ON p.id = r.payment_id
    WHERE r.status = 'PENDING'
      AND r.amount <> p.amount - COALESCE(p.discount_amount, 0);

    IF blocked > 0 THEN
        RAISE EXCEPTION 'release gate blocked: % legacy partial refund attempt(s) require finance reconciliation', blocked;
    END IF;
END $$;

DO $$
DECLARE
    blocked INTEGER;
BEGIN
    SELECT count(*) INTO blocked
    FROM privacy_object_deletion_jobs
    WHERE storage_scope = 'UNVERIFIED_LEGACY'
      AND status <> 'COMPLETED';

    IF blocked > 0 THEN
        RAISE EXCEPTION 'release gate blocked: % unknown-scope media deletion job(s) require operator verification', blocked;
    END IF;
END $$;

SELECT count(*) AS legacy_unverified_sos_alerts
FROM emergency_alerts
WHERE delivery_status = 'LEGACY_UNVERIFIED';

SELECT 'release gate passed' AS result;
