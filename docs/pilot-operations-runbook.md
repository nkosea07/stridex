# Pilot operations and recovery runbook

This is a procedure template. It is not evidence that a backup, restore, rollback,
or live incident drill has been exercised. Record the operator, environment,
timestamps, artifact checksums, and outcome for each rehearsal before pilot approval.

## Before deploying

1. Confirm the release commit, immutable image digests, migration list, and the
   approved `.env` source in the secret manager. Never attach secrets to this record.
2. Confirm PostgreSQL/PostGIS is reachable, Redis has persistence and a tested
   recovery path, and the web origin is HTTPS. The Compose web port is HTTP for
   private-network use; production must terminate TLS upstream and forward the
   original HTTPS origin correctly for Secure refresh cookies and origin checks.
3. Take a database backup and verify its checksum before applying migrations.
4. Compare the live schema with the exact V1 baseline and all migrations through
   the repository migration head (currently V42). Never run `baseline` or `repair` as a way to bypass a
   schema mismatch. Existing databases may be baselined at version 1 only after
   a DBA has recorded and approved a structural diff against `V1__baseline.sql`.
5. Confirm global RFID simulation is disabled in production and any simulated
   race has explicit pilot authorization.
6. Before dispatching deployment, provision the `race-tracker` namespace, PostgreSQL and Redis services, and `race-tracker-secrets`; the workflow fails preflight if any are missing or contain placeholder values. Set `AWS_S3_BUCKET` to the selected real bucket and `FRONTEND_URL` to the public HTTPS origin. Set `MAIL_ENABLED=true` for production with verified `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, and secret `MAIL_USERNAME`/`MAIL_PASSWORD`; staging may use `false`. The workflow rejects missing/placeholder inputs before applying manifests and starts the backend with the `prod` profile. Record the rendered ConfigMap validation result; do not apply `deploy/k8s/config.yaml` directly.
7. Before V35, reconcile legacy `PENDING` refund attempts and duplicate non-null provider references against provider records. Automated refunds remain disabled. A super admin must stage each new full refund against a specific completed payment, perform or verify the external provider action, and record the receipt/case reference and outcome against that refund attempt; never infer success from a local pending row.
8. Before deploying migrations V37–V42, run `psql "$DATABASE_URL" -f scripts/release-gate.sql` and keep the result with the release evidence. The gate blocks partial refund attempts and unknown-scope media jobs. For diagnosis, run this read-only query:

   ```sql
   SELECT r.id, r.payment_id, r.amount, r.status, r.provider_ref, r.created_at,
          p.amount, p.discount_amount, p.currency
   FROM payment_refunds r
   JOIN payments p ON p.id = r.payment_id
   WHERE r.status = 'PENDING'
     AND r.amount <> p.amount - COALESCE(p.discount_amount, 0);
   ```

   Any row blocks pilot rollout until finance resolves it against provider records. Do not use the standard reconciliation endpoint to confirm a partial attempt as a full refund; it returns `REFUND_AMOUNT_MISMATCH` by design. Record the provider receipt, the amount actually returned, the operator, and the resolution decision in the finance case log. A reviewed correction must preserve the original partial amount in the audit trail.
   A provider `REVERSED` callback creates a `payment_reversal_cases` row and leaves the payment completed pending verification. Alert on `payments_reversal_review_open`; finance verifies the provider record in the super-admin **Payment reversals** view, then records a refund attempt outcome or documents why no refund is due. Do not treat a reversal row as evidence of a full refund.
9. Confirm the SOS notification path is live before race day: staff assignments, email/push configuration, and the escalation gauges below must be alerting to an on-call owner. Confirm the rendered deployment manifests before any cluster apply; all rendering, image-tag, secret, namespace, storage, and service checks run before the first `kubectl apply`.

## Backup and restore rehearsal

Use a managed-database snapshot or `pg_dump` custom-format backup stored in the
approved encrypted backup location. Do not place database credentials in shell
history or this runbook.

```sh
pg_dump --format=custom --no-owner --file="race-tracker-<timestamp>.dump" "$DATABASE_URL"
sha256sum "race-tracker-<timestamp>.dump"
pg_restore --list "race-tracker-<timestamp>.dump"
```

Restore only into a disposable database first, using the approved secret store
for connection credentials. Run Flyway validation, application schema validation,
and representative race/result/RFID queries against the restored copy. Record
the restored database version and compare critical row counts; do not overwrite
the source database during rehearsal.

## Deployment and rollback

- Deploy compatible application images only after backup verification.
- Run forward-only Flyway migrations; do not use destructive down scripts.
- For an application rollback, deploy the prior image only if it is compatible
  with the expanded schema. V37–V41 are additive; a prior image must tolerate the
  new columns and tables. Otherwise restore the verified backup into an isolated
  database, validate it, and perform the approved cutover.
- Preserve the failed image digest, application logs, migration history, and
  correlated audit/request IDs for incident analysis.

## Incident response and key rotation

- Restrict access to the affected race and preserve audit, gateway spool, and
  database evidence before changing state.
- For suspected JWT key exposure, issue a new key through the secret manager and
  plan token invalidation/re-authentication; do not log either key.
- For suspected MFA encryption-key exposure, stop enrollment/reset operations,
  preserve a protected backup of the old key, rotate with a reviewed re-encryption
  or recovery procedure, and require affected users to re-enroll where safe
  decryption cannot be proven.
- For reader-key exposure, revoke the reader, record the audit event, issue a new
  key, and validate ingest with a non-production race before resuming timing.

## Privacy object cleanup

- An overdue `privacy_object_cleanup_overdue_jobs` gauge above zero means an account cannot be marked fully erased. Alert the privacy/ops owner and inspect `privacy_object_deletion_jobs` for `status <> 'COMPLETED'`, `attempts`, `next_attempt_at`, and `lease_until`; restrict access because storage keys are private data.
- Confirm the selected storage provider and credentials, then let the idempotent worker retry. Do not manually mark a job complete unless an operator has verified the object is absent in the selected bucket and recorded the evidence. Do not send a completion notice or mark the account deleted while any object job is unfinished.
- After restoration or worker restart, verify expired leases become claimable, missing objects finish safely, and the overdue gauge returns to zero. Record affected account IDs and remediation in the incident log without copying object keys into public tickets.
- A storage provider, endpoint, bucket, or local root change leaves older jobs pending until the original location is restored or a verified migration is performed. Do not acknowledge a job deleted in a different storage scope.
- Media uploaded before V36 has no stored upload scope. Its erasure jobs use `UNVERIFIED_LEGACY` and cannot complete automatically. A super admin reviews them in **Privacy object scopes**, confirms the historical bucket or directory from deployment and storage evidence, and records the verified scope with a reason. Never default legacy jobs to the currently configured bucket.
- Record the verification in an audit event before changing a job scope or completing it. Block pilot rollout while any unknown-scope job lacks a verified remediation path, and do not delete from the current provider on a guess.
- Failed finalization records `finalization_attempts`, `finalization_error_code`, and `finalization_next_attempt_at`. Inspect `privacy.erasure.finalization.failed`; the worker retries with backoff and repairs previously finalized accounts in batches. Do not reset an account to a delivered state while its object jobs are unfinished.
- A `privacy_erasure_notice_overdue` alert means the completion notice has not been accepted by the mail provider. The worker retains the address and retries; mail-disabled deployments must not be considered notice-delivery complete.

## Emergency SOS escalation

- An alert is `QUEUED` until at least one staff notification is dispatched, `DISPATCHED` once a channel succeeds, and `ESCALATION_REQUIRED` when there are no eligible recipients or every job exhausts its attempts.
- Alert on `emergency.notification.overdue` (pending more than two minutes), `emergency.notification.exhausted`, and `emergency.alert.escalation.required`. The race-day on-call operator owns escalation.
- On escalation, contact assigned operational staff and the race director through the documented out-of-band phone tree. Record who was contacted and when. The race live-tracking console shows active SOS alerts for super admins, race admins, and officials. Do not mark the alert resolved until staff acknowledge it.
- Staff acknowledge or resolve from the live-tracking SOS panel, or through `PATCH /api/v1/races/{raceId}/emergency/{alertId}` with `{"status":"ACKNOWLEDGED"}` or `{"status":"RESOLVED"}`. Resolving cancels pending notification jobs and removes the alert from the escalation and delivery gauges. Historical alerts marked `LEGACY_UNVERIFIED` are excluded from critical escalation counts but remain visible for review.
- `emergency.notification.backlog` shows waiting jobs. A rising backlog with a stale heartbeat indicates the dispatcher is not running; restore the worker before manually retrying anything.
- Athletes are told "SOS received; organizer delivery is not yet confirmed" until delivery is confirmed. Never tell an athlete that a location was shared when no recent device fix was attached.

## Public media consent and attestation

- Public participant media requires both moderator approval and current participant consent. Revocation hides the listing, original, and thumbnail immediately; do not republish cached copies.
- Untagged event media stays private until an authorized administrator records a non-person attestation with a reason through `attest-non-person`. A participant-linked image must use participant consent instead. The media console shows public/private state and explains when approved media is hidden for missing consent or attestation; revocation is audited.
- The public media responses are `no-store`; do not add a CDN or cache layer that can serve media after consent is revoked.

## RFID race-day recovery

- Check mode, heartbeat age, queue depth/age, and failed-event count in the gateway
  health endpoint and operator view.
- Keep completed events immutable. Replay only events explicitly recorded as
  failed, and verify each replay is idempotent by `(reader_id, source_event_id)`.
- If the gateway is unavailable, preserve its durable spool volume and follow the
  timing operator's paper/manual fallback. Do not switch to hardware mode with
  placeholder serial, endpoint, key, or profile values.

## Required evidence before pilot approval

- A clean PostGIS migration and supported legacy upgrade have both passed.
- A backup has been restored to an isolated database and checked by an operator.
- A compatible application rollback or database restore/cutover has been rehearsed.
- Authentication, organizer approval, public-feed load/freshness, device recovery,
  simulated RFID outage/replay, and full race-day rehearsal results are recorded.
