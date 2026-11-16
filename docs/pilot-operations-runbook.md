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
   the repository migration head (currently V58). Never run `baseline` or `repair` as a way to bypass a
   schema mismatch. Existing databases may be baselined at version 1 only after
   a DBA has recorded and approved a structural diff against `V1__baseline.sql`.
5. Confirm the legal operator placeholders are replaced and reviewed, and that deployed `TRACKING_RETENTION_DAYS` and `PRIVACY_DELETION_GRACE_DAYS` match the published Privacy Policy (defaults 90 and 30 days). Changing either document's text requires bumping its version in `contract.json` and `LegalDocuments.java`, which re-prompts every signed-in user. Clients gate on the server's `legalAcceptanceRequired` flag, so older app builds are not trapped after a bump; they show an update/reload message if they try to accept outdated text. GPS retention deletes in batches (`racetracker.tracking.retention-batch-size`, default 5000; up to `retention-max-batches-per-run`, default 200, per hourly run). The first runs after enabling it work through any backlog of points older than 90 days, and race playback is unavailable for those points once they are deleted.
6. Confirm global RFID simulation is disabled in production and any simulated
   race has explicit pilot authorization.
7. Before dispatching deployment, provision the `race-tracker` namespace, PostgreSQL and Redis services, and `race-tracker-secrets`; the workflow fails preflight if any are missing or contain placeholder values. Set `AWS_S3_BUCKET` to the selected real bucket and `FRONTEND_URL` to the public HTTPS origin. Set `MAIL_ENABLED=true` for production with verified `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, and secret `MAIL_USERNAME`/`MAIL_PASSWORD`; staging may use `false`. The workflow rejects missing/placeholder inputs before applying manifests and starts the backend with the `prod` profile. Record the rendered ConfigMap validation result; do not apply `deploy/k8s/config.yaml` directly.
8. Before V35, reconcile legacy `PENDING` refund attempts and duplicate non-null provider references against provider records. Automated refunds remain disabled. A super admin must stage each new full refund against a specific completed payment, perform or verify the external provider action, and record the receipt/case reference and outcome against that refund attempt; never infer success from a local pending row.
9. Before deploying migrations V37–V42 or V52, run `psql "$DATABASE_URL" -f scripts/release-gate.sql` and keep the result with the release evidence. The gate blocks partial refund attempts and unknown-scope media jobs. For diagnosis, run this read-only query:

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
10. Confirm the SOS notification path is live before race day: staff assignments, email/push configuration, and the escalation gauges below must be alerting to an on-call owner. Confirm the rendered deployment manifests before any cluster apply; all rendering, image-tag, secret, namespace, storage, and service checks run before the first `kubectl apply`.

## Backup and restore rehearsal

Run `scripts/backup-restore-drill.sh` with `SOURCE_DATABASE_URL` and an empty, disposable
`RESTORE_DATABASE_URL`. It dumps, checksums, restores, compares row counts for the critical
tables, checks the Flyway history, and writes `evidence.txt`; it refuses to overwrite a
non-empty target and never writes to the source. Keep the evidence file with the release record.

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
  with the expanded schema. V37–V41, V43 (nullable `auth_tokens.family_id`), V44 (RFID
  processing attempt columns with defaults), V45 (adds the `SKIPPED` SOS job status), and V46
  (nullable `import_jobs.row_errors`) are additive. V47 narrows bib-number and RFID-tag
  uniqueness from all races to each race; a prior image still runs, but it keeps rejecting
  numbers that another race already uses. V48 (nullable `notifications.subject_participant_id`,
  backfilled from notification data) V49 (new `import_job_sources` table), V50 (nullable `users.terms_version` and `terms_accepted_at`), and V51 (`checkpoints.self_scan_token` backfilled with a random secret, new `self_scan_claims` table), and V52 (`IGNORED` read status, `races.started_at`, `rfid_readers.clock_offset_ms`, canonical RFID tag values), V53 (result versions, certification lock, correction history, participant status reason), V54 (race waves), V55 (checkpoint passes; the participant/checkpoint unique key now includes `pass_number`), V56 (athlete result queries), V57 (voided timing records: `checkpoint_records.voided_at/voided_by/void_reason`, the active-pass unique key becomes a partial index, `VOIDED` read status, correction `action`), and V58 (`users.preferred_locale`, `account_erasures.notification_locale`) are additive; a prior image would count voided records as active times, so do not roll back below V57 after records have been voided; a prior image ignores waves, passes, versions, and queries, and keeps rejecting a second record for a checkpoint; a prior image ignores the token, but it still accepts self-scans without a code or a location check; a
  prior image ignores the new column and still writes imports to local disk; a prior image must tolerate the new columns and tables. Refresh tokens issued
  by a prior image have no session family and are treated as single-token sessions. Otherwise restore the verified backup into an isolated
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

- An alert is `QUEUED` while staff notifications are being attempted. It becomes `DISPATCHED` only when an external provider (push, email, or WhatsApp) accepted at least one staff notification and no delivery job exhausted its retries. An in-app notification alone does not count, because nobody may be looking at the console. Otherwise it becomes `ESCALATION_REQUIRED` (no eligible recipients, no external provider acceptance, or any job exhausting its attempts).
- Staff without a registered device, email address, or phone for a channel are recorded as `SKIPPED` for that channel, not as delivered. Staff whose account was deactivated, is pending deletion, or was erased after the alert was raised are also skipped. If no channel can reach anyone, the alert escalates.
- An athlete pressing SOS again within 10 minutes of an unresolved alert updates that alert's location instead of creating a new alert and re-notifying staff. A resolved alert cannot be reopened; a new SOS creates a new alert.
- Alert on `emergency.notification.overdue` (pending more than two minutes), `emergency.notification.exhausted`, and `emergency.alert.escalation.required`. The race-day on-call operator owns escalation.
- On escalation, contact assigned operational staff and the race director through the documented out-of-band phone tree. Record who was contacted and when. The race live-tracking console shows active SOS alerts for super admins, race admins, and officials. Do not mark the alert resolved until staff acknowledge it.
- Staff acknowledge or resolve from the live-tracking SOS panel, or through `PATCH /api/v1/races/{raceId}/emergency/{alertId}` with `{"status":"ACKNOWLEDGED"}` or `{"status":"RESOLVED"}`. Resolving cancels pending notification jobs and removes the alert from the escalation and delivery gauges. Historical alerts marked `LEGACY_UNVERIFIED` are excluded from critical escalation counts but remain visible for review.
- `emergency.notification.backlog` shows waiting jobs. A rising backlog with a stale heartbeat indicates the dispatcher is not running; restore the worker before manually retrying anything.
- Athletes are told "SOS received; organizer delivery is not yet confirmed" until delivery is confirmed. Never tell an athlete that a location was shared when no recent device fix was attached.

## Public media consent and attestation

- Public participant media requires both moderator approval and current participant consent. Revocation hides the listing, original, and thumbnail immediately; do not republish cached copies.
- Untagged event media stays private until an authorized administrator records a non-person attestation with a reason through `attest-non-person`. A participant-linked image must use participant consent instead. The media console shows public/private state and explains when approved media is hidden for missing consent or attestation; revocation is audited.
- The public media responses are `no-store`; do not add a CDN or cache layer that can serve media after consent is revoked.

## Results release, certification, and queries

- Publishing creates a new results version: an immutable copy of the results (no personal data) with a SHA-256 fingerprint chained to the previous version. Versions cannot be edited or deleted in the database.
- Before certifying, review **Timing review** (finishers missing a timed mat, impossibly fast segments, out-of-order crossings) and answer every **Athlete result query**. Certification is refused while queries are open, while timing has changed since the last publish, or if the published results no longer match their fingerprint.
- **Certify as official** locks timing records, corrections, penalties, result-affecting participant details, checkpoints, waves, gun times, self-scan approvals, and RFID reprocessing. New RFID reads are kept but ignored. To change anything, **Reopen results** with a reason (for example an upheld protest), make the change, publish, and certify again. Every step is audited.
- A results integrity failure ("published results no longer match their recorded fingerprint") means the table was changed outside the platform. Do not certify; preserve the database, investigate, and republish.
- A time that belongs to the wrong athlete (swapped chips, misread bib) is never deleted. In the checkpoint's **Records** dialog choose **Void** (removed from results, kept with your reason, its RFID reads are marked `VOIDED` so a reprocess or gun correction cannot recreate it) or **Reassign** (voids it and records the same time for the bib you enter). **Restore** undoes a void unless the athlete already has a time there. Voiding a finish returns the athlete to STARTED; voiding the read that missed a cutoff reverses the cutoff DNF. Laps are renumbered in time order. All three need a reason, are audited, and are blocked while results are certified.
- Time corrections require a reason and keep full history. Status decisions (DQ, DNF, DNS, finished) require a reason. Checkpoints and participants with timing data cannot be deleted; withdraw the entry (DNS) instead. Only draft races can be deleted.

## Wave starts and laps

- Waves have their own gun. Create waves on the RFID page, assign bib ranges, and press **Fire gun now** at each start. Athletes in a wave are timed from their wave gun; reads before it are ignored. Correcting a wave or race gun time moves automatic times that fall before the new gun to the earliest valid read, or removes them, and re-runs ignored reads.
- For loop courses, set a checkpoint's **Times crossed (laps)** and minimum seconds between crossings. Passes are derived from all reads, so late or out-of-order reads land on the right lap; only the final crossing of a finish checkpoint finishes the athlete. For a shared start/finish mat, mark the finish checkpoint so its first crossing is the chip start.

## Packet pickup and offline staff capture

- **Chip assignment** (race page → RFID timing → Chip assignment) assigns chips at packet pickup with a desktop RFID reader in keyboard mode. Every assignment is audited; a chip already assigned to someone else is refused.
- **Athlete check-in on a phone** (Operations → Open athlete check-in): search by bib or name (at most 20 matches, no contact or medical details), or scan the QR code on the athlete's confirmation. Only confirmed or registered entries can be checked in (`CHECK_IN_NOT_ELIGIBLE` otherwise); send others to the registration desk. Every check-in is audited. Staff need a race staff assignment for the race; checkpoint-only volunteers cannot check athletes in.
- **Recent entries and undo**: the Operations screen lists the last 20 entries at the selected checkpoint. Staff can undo their own manual or QR entry within 5 minutes (wrong bib, duplicate); it is voided with the reason, not deleted, and audited. After 5 minutes, or for chip reads and other people's entries, a race admin voids or reassigns it on the web.
- **Wave starts from the start line**: an official assigned to the race can start a wave that has not started; the gun time is the moment they confirm. Correcting or backdating a gun time stays with race admins on the web.
- Staff checkpoint captures made without a connection are saved on the phone with the tap time and sent automatically when the connection returns. The server accepts offline captures up to 24 hours old, never in the future or before the athlete's gun, and audits them. Signing out clears the queue, so send pending captures first.

## Athlete payments and account recovery in the mobile app

- Athletes pay the entry fee from the race page (**Pay now**). The app opens the provider checkout in an in-app browser; after payment the provider redirects to `PAYMENTS_RETURN_URL`, which should be a page that tells the athlete to close it and return to the app. The entry shows as paid only after the provider callback reaches `PAYMENTS_RESULT_URL`.
- Checkout is refused for waitlisted, rejected, or disqualified entries (`PAYMENT_NOT_AVAILABLE_FOR_STATUS`). Registrations expose `amountDue` (current fee, including early-bird and team fees) while unpaid.
- **Forgot password** and **Resend verification email** are available on the mobile sign-in screen. Both are rate-limited per account and per IP address; reset links open the web app's reset page.
- Finisher certificates appear in the app once results are certified (official).

## Mobile app minimum version

- Set `MOBILE_MINIMUM_VERSION` (for example `1.4.0`) when an app release must be retired, and `MOBILE_IOS_STORE_URL` / `MOBILE_ANDROID_STORE_URL` so the update screen links to the store. Blank disables the check.
- Older apps show a full-screen **Update required** prompt at launch (from `GET /api/v1/public/app-config`), and any API call they make is refused with HTTP 426 `APP_UPDATE_REQUIRED`. Browsers and integrations never send `X-App-Version` and are not affected.
- Publish the new store build and wait for it to be live before raising the minimum. Restart the backend to apply a new value.

## Admin two-factor sign-in

- Super admins and race admins enrol an authenticator app at their next sign-in and save the one-time recovery codes. A lost device is handled by another super admin resetting MFA for that account (`/admin/users/{userId}/mfa/reset`). Admin accounts cannot sign in on the mobile app.
- Rotate a leaked RFID reader key from the RFID page (**Rotate key**); the old key stops working immediately, so update the gateway at once.

## Athlete checkpoint self-scans

- Self-scan QR codes changed in V51. Codes printed before V51 contain only the checkpoint and race IDs, and the app now rejects them as out of date. Before race day, download each intermediate checkpoint's code from **Checkpoints → Self-scan QR code** and replace the printed copy.
- Start and finish checkpoints have no self-scan code: their times come only from RFID, staff entry, or timing import. A race with no RFID or staff at the finish must staff it.
- Treat printed codes as secrets. If a photo circulates, rotate the code (race admin), then replace the printed copy. Rotation invalidates every earlier copy immediately.
- Scans the location check cannot confirm appear in **Self-scans awaiting review** on the Checkpoints page. They do not affect status or results until an official approves them. Review them before publishing results; claims for checkpoints with official timing close automatically.

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
