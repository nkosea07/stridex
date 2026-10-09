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
   the deployed version. Never run `baseline` or `repair` as a way to bypass a
   schema mismatch. Existing databases may be baselined at version 1 only after
   a DBA has recorded and approved a structural diff against `V1__baseline.sql`.
5. Confirm global RFID simulation is disabled in production and any simulated
   race has explicit pilot authorization.

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
  with the expanded schema. Otherwise restore the verified backup into an isolated
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
