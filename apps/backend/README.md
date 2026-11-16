# Race Tracker Backend

Spring Boot API for race administration, participant tracking, results, and notifications.

## Stack
- Java 17
- Spring Boot 3.5
- Spring Security (JWT)
- Spring Data JPA (PostgreSQL)
- PostGIS for spatial query fields and proximity indexes
- Spring WebSocket
- Spring Boot Actuator
- Micrometer Observation + Prometheus metrics
- MapStruct
- springdoc OpenAPI + Swagger UI

## Run
```bash
cd apps/backend
mvn spring-boot:run
```

The backend runs under the `/api` servlet context path, so versioned controller routes are exposed under `/api/v1`.

Management endpoints also use the same context path and are exposed under `/api/actuator`.

## Configuration
Main config file:
- `src/main/resources/application.yml`

Required environment variables:
- `JWT_SECRET`

Identity configuration:
- Development and test enable the existing Google OAuth implementation through `racetracker.oauth.enabled`.
- The `prod` profile sets `racetracker.oauth.enabled=false` and the production validator rejects OAuth if it is enabled, so production uses password authentication without an MFA prompt in the current release.
- The legacy MFA API and stored credentials remain available for compatibility, but normal web and mobile login no longer creates or requires an MFA challenge.

Common database variables:
- `DB_HOST` (default `localhost`)
- `DB_PORT` (default `5432`; the local Docker Compose PostGIS in `apps/backend/.env` uses host port `5435`)
- `DB_NAME` (default `race_tracker`)
- `DB_USERNAME` (default `race_admin`)
- `DB_PASSWORD` (default `localdevpassword` for local Docker Compose only)

For the `prod` Spring profile, `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `FRONTEND_URL` are required. Production accepts only explicit HTTPS CORS origins, validates the JWT secret at startup, disables development seeding, and disables Swagger/OpenAPI endpoints.

Database requirements:
- PostgreSQL must provide PostGIS; the Flyway baseline enables it with `CREATE EXTENSION IF NOT EXISTS postgis`.
- Decimal latitude/longitude columns remain the API compatibility fields.
- `geography(Point, 4326)` columns on races, checkpoints, and GPS locations are query fields for spatial operations, maintained from decimal coordinates by database triggers.
- GiST indexes support the intended `ST_DWithin` proximity path for checkpoint/race-day location checks.
- Versioned migrations in `src/main/resources/db/migration` are authoritative. Hibernate validates the migrated schema and does not update it outside the explicit local `dev` profile.
- Flyway does not automatically baseline non-empty databases. Before adopting it on an existing environment, take a verified backup, compare the full schema with `V1__baseline.sql` plus the intended upgrades, and manually record the matching Flyway baseline version. Do not baseline based only on table names or apply the old date-named SQL scripts blindly; their deployment history is not tracked here.
- `V3__organizer_applications_and_rfid_scale.sql` checks for duplicate `(reader_id, source_event_id)` groups before creating the idempotency index. Reconcile duplicates from its diagnostic output without deleting/merging timing records, then rerun.
- `gps_locations` partitioning is intentionally deferred to a dedicated migration on `recorded_at` so existing JPA/API behavior stays stable.
- `V35__refund_reconciliation.sql` adds full-refund reconciliation constraints; `V36__privacy_object_erasure.sql` adds durable object cleanup jobs. Validate both on an isolated PostGIS database before deployment. Reconcile existing pending refund attempts and duplicate provider references before applying V35.

Mobile and localization:
- `MOBILE_MINIMUM_VERSION`, `MOBILE_LATEST_VERSION`, `MOBILE_IOS_STORE_URL`, `MOBILE_ANDROID_STORE_URL` (all optional). Requests carrying `X-App-Version` below the minimum get HTTP 426 `APP_UPDATE_REQUIRED`; `GET /api/v1/public/app-config` (anonymous) returns these values.
- Emails and notifications are rendered in the recipient's `users.preferred_locale` through `MessageSource` (`src/main/resources/messages*.properties`, English is the fallback). The locale is taken from `Accept-Language` at sign-up and updated when the user changes language in the web or mobile app (`PUT /api/v1/auth/profile` with `preferredLocale`, `UNSUPPORTED_LOCALE` otherwise). `racetracker.i18n.supported-locales` must match `contract.json` (checked by `pnpm contracts:validate`). Add a language by adding `messages_<lang>.properties`; email templates use `#{...}` keys only.

For a clean local database, start the PostGIS service with `docker compose up -d db redis`, then run the backend. Flyway applies the clean baseline and forward migrations. Do not run `database/schema.sql` separately.

## Management and Observability

Actuator is configured as the backend operational surface:
- `GET /api/actuator/health` is public for load balancers and uptime checks.
- `GET /api/actuator/health/liveness` is public for liveness probes.
- `GET /api/actuator/health/readiness` is public for readiness probes.
- `GET /api/actuator/info` is public, but limited to explicit safe app metadata and optional build metadata.
- `GET /api/actuator/prometheus` is exposed for metrics scraping but requires authentication.

The public tracking and tracking ingest hot paths are instrumented with Micrometer `@Observed` names:
- `tracking.public.race_hub`
- `tracking.public.live_athletes`
- `tracking.public.athlete_detail`
- `tracking.ingest.location`
- `tracking.ingest.location_batch`
- `tracking.ingest.checkpoint`
- `tracking.live.aggregate`
- `tracking.live.participant_track`

This phase does not configure an external OpenTelemetry collector/exporter. The geospatial
schema foundation, backend RFID bridge ingest, and reader administration API are present;
telemetry partitioning, vendor SDK adapters, and CSV timing imports remain deferred work.
For the current cross-surface status, use `docs/PENDING_WORK.md`.

## API Surface (Implemented)

### Auth
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/register-organizer`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/deletion/cancel` accepts email and password for a signed-out account during its deletion grace period. The response is generic and issues no session.
- `DELETE /api/v1/me` schedules self-service deletion. It requires the current password and the confirmation `DELETE`, is rate limited per account and IP (5 and 20 per 15 minutes), is serialized with a transaction-scoped advisory lock so concurrent requests cannot both pass the blocker checks, deactivates the account, revokes every session and push device, and erases the account after `PRIVACY_DELETION_GRACE_DAYS` (default 30). Failures return 400 `ACCOUNT_DELETION_PASSWORD_REQUIRED`, `ACCOUNT_DELETION_PASSWORD_INVALID` (deliberately not 401, so clients do not treat it as an expired session), or `ACCOUNT_DELETION_CONFIRMATION_REQUIRED`; 409 `ACCOUNT_ALREADY_DELETED`; or 429 `TOO_MANY_REQUESTS`.
- Deletion is refused with 409 while it would strand operations: `ACCOUNT_DELETION_LAST_SUPER_ADMIN` when no other active super admin exists, and `ACCOUNT_DELETION_ACTIVE_RACES` when the user is the only active organizer (creator or assigned race admin) of a race in `REGISTRATION_OPEN`, `REGISTRATION_CLOSED`, or `IN_PROGRESS`. `GET /api/v1/me/deletion` returns the same codes in `blockers` so clients can explain them before the user confirms. `POST /api/v1/me/deletion/cancel` cancels a pending request for a signed-in session.
- `POST /api/v1/auth/refresh`

Sessions are per device: signing in creates a refresh-token family, and each refresh atomically
consumes the presented token and issues the next one in the same family, so only one of two
concurrent refreshes succeeds. A rotated token presented again within 30 seconds (two browser tabs
refreshing at once) gets a plain 401; later reuse revokes that device's family and writes a
`REFRESH_TOKEN_REUSE` audit event. Other devices stay signed in; password reset and account
deletion revoke every session. Login returns `EMAIL_VERIFICATION_REQUIRED` only when the password
is correct; any wrong password returns the same generic `Invalid credentials` response.
- `GET /api/v1/auth/me`
- `PUT /api/v1/auth/profile`

Both registration paths require `privacyPolicyAccepted=true`, `termsAccepted=true`, and the current `privacyPolicyVersion` and `termsVersion` from `packages/contracts/contract.json` (mirrored in `LegalDocuments`). Missing acceptance returns `PRIVACY_CONSENT_REQUIRED` or `TERMS_ACCEPTANCE_REQUIRED`; a stale version returns `PRIVACY_POLICY_VERSION_MISMATCH` or `TERMS_VERSION_MISMATCH`. Older clients must be updated.

Signed-in users record acceptance of the current documents with `POST /api/v1/me/legal-acceptance` (`{"privacyPolicyVersion","termsVersion"}`); it rejects non-current versions, stores both versions and timestamps on the user, and writes a `LEGAL_DOCUMENTS_ACCEPTED` audit event. `UserDto` exposes `privacyPolicyVersion`, `termsVersion`, and the server-computed `legalAcceptanceRequired`; web and mobile prefer that flag over their bundled versions (so an older build is never trapped after a version bump) and block the app behind an acknowledgement screen until both match the contract. Active GPS tracking is not interrupted.

### External refunds and privacy erasure

- Checkout validates the registration and stages a `PENDING` payment, calls Pesepay with no database transaction open, then records the provider reference; a provider failure marks the staged payment `FAILED` and returns `PAYMENT_PROVIDER_UNAVAILABLE` (HTTP 503) without exposing provider error text. Production rejects a sandbox or non-HTTPS `PESEPAY_BASE_URL` when payments are enabled.
- `POST /api/v1/races/{raceId}/participants/{participantId}/payment/refund` returns `REFUND_AUTOMATION_UNAVAILABLE`; it does not call the payment provider.
- A super admin stages a full-payable refund attempt at `POST .../payment/refund/attempts` with a reason and an optional completed `paymentId`, completes or verifies the action outside the platform, then records `REFUNDED` or `NOT_REFUNDED` at `POST .../payment/refund/attempts/{attemptId}/reconcile` with a provider receipt/case reference and reason. `GET .../payment/refund/attempts` and `GET .../payment/refund/attempts/{attemptId}` expose attempt status. The legacy `POST .../payment/refund/attempt` and `.../payment/refund/reconcile` routes remain as v1 compatibility wrappers that require one unambiguous payment or pending attempt. The server computes the amount and locks the payment; the UI does not perform a one-click refund.
- Provider `REVERSED` callbacks create an open `payment_reversal_cases` row and leave the payment completed pending finance verification. Super admins review them at `GET /api/v1/admin/payments/reversal-cases`; the metric is `payments_reversal_review_open`. Partial legacy refund attempts cannot be reconciled as full refunds (`REFUND_AMOUNT_MISMATCH`).
- Super admins can list blocked unknown-scope erasure jobs at `GET /api/v1/admin/privacy/object-deletion-jobs?scope=UNVERIFIED_LEGACY` and record a verified historical scope at `PATCH .../{jobId}/scope` with a reason. Both operations are audited; a changed scope only retries when it matches the configured storage provider.
- Due account deletion first removes media metadata and persists original/thumbnail deletion jobs. The cleanup worker retries private storage deletion and only then finalizes account anonymization and sends the completion notice. Monitor `privacy_object_cleanup_overdue_jobs` and follow `docs/pilot-operations-runbook.md` for failures.
- Participant media consent is managed at `PUT /api/v1/participants/my-registrations/{participantId}/media-consent`. Public listings, originals, and thumbnails require moderator approval plus current participant consent; untagged event media requires an audited `attest-non-person` record, which can be revoked with `DELETE` on the same route.
- Anonymous race-scoped routes, tracking, results, registration fields, and media return 404 unless public registration is enabled, the race is non-draft, and it is unarchived.

### Races
- `GET /api/v1/races`
- `GET /api/v1/races/{id}`
- `POST /api/v1/races`
- `PUT /api/v1/races/{id}`
- `PATCH /api/v1/races/{id}/status`

In-progress races are closed automatically (and still-running participants marked DNF) only after
the latest scheduled start (race start, or the last stage's start) plus the course time limit plus
a grace period, evaluated in the race timezone. The time limit is the longest checkpoint cutoff,
otherwise `RACE_DEFAULT_TIME_LIMIT_HOURS` (default 24); the grace is
`RACE_COMPLETION_GRACE_MINUTES` (default 60). A race without a start time is never closed before
its local day has ended. Officials can still complete a race manually through the status endpoint.
- `DELETE /api/v1/races/{id}`

### Emergency SOS
- `POST /api/v1/races/{raceId}/checkpoints/{checkpointId}/emergency` for authorized checkpoint staff
- `POST /api/v1/races/{raceId}/emergency`
- `GET /api/v1/races/{raceId}/emergency`
- `PATCH /api/v1/races/{raceId}/emergency/{alertId}` with `ACKNOWLEDGED` or `RESOLVED`

SOS alerts are persisted before durable staff notification jobs are staged. Coordinates are optional but must be a complete, in-range pair. Delivery is retried with backoff; no recipients or exhausted attempts set `ESCALATION_REQUIRED`, and resolving an alert cancels pending jobs. Legacy alerts are classified `LEGACY_UNVERIFIED` and excluded from critical escalation counts.

### Participants and Registration
- `GET /api/v1/races/{raceId}/participants`
- `GET /api/v1/races/{raceId}/participants/{id}`
- `POST /api/v1/races/{raceId}/participants`
- `PUT /api/v1/races/{raceId}/participants/{id}`
- `DELETE /api/v1/races/{raceId}/participants/{id}`
- `POST /api/v1/races/{raceId}/participants/{id}/check-in`
- `POST /api/v1/races/{raceId}/participants/import`
- `GET /api/v1/races/{raceId}/participants/export`

Imports accept `.csv`, `.xlsx`, or `.xls` (15 MB). The upload is stored with its job in the
database (`import_job_sources`, removed when the job finishes or permanently fails), so any backend
instance can process or resume it; processing starts after the request commits. Jobs created
before V49 still read their original local file. Headers match case-insensitively and ignore a UTF-8
byte-order mark (`First Name`, `first_name`, and `firstName` are equivalent). Each row is validated
the same way in preview and import (required names and email, email format, ISO `dateOfBirth`,
`gender`, `status`, `isTrackingEnabled`, `categoryId`, field lengths) and duplicates are detected
by email, bib number, and RFID tag within the race. Skipped rows are recorded with a reason on the
job (`rowErrors`, first 200). Bib numbers and RFID tags are unique per race, not across races;
manual create/update returns `BIB_NUMBER_IN_USE` or `RFID_TAG_IN_USE` (409) on a clash.

Participant CSV and XLSX exports are capped at 10,000 participants per race for the pilot. Larger exports return `EXPORT_TOO_LARGE` (HTTP 400); export generation failures return `EXPORT_GENERATION_FAILED` (HTTP 503).
- `POST /api/v1/races/{raceId}/register`
- `GET /api/v1/participants/my-registrations`
- `DELETE /api/v1/registrations/{registrationId}`

### Checkpoints
- `GET /api/v1/operations/checkpoints?page=0&size=50` for the authenticated volunteer (maximum size 100)
- `GET /api/v1/races/{raceId}/checkpoints`
- `POST /api/v1/races/{raceId}/checkpoints`
- `PUT /api/v1/races/{raceId}/checkpoints/{checkpointId}`
- `DELETE /api/v1/races/{raceId}/checkpoints/{checkpointId}`
- `POST /api/v1/races/{raceId}/checkpoints/{checkpointId}/record`
- `GET /api/v1/races/{raceId}/checkpoints/{checkpointId}/records?includeVoided=true` (voided rows only for race managers)
- `GET /api/v1/races/{raceId}/checkpoints/{checkpointId}/records?limit=20` latest entries first (max 100), for checkpoint staff
- `GET /api/v1/races/{raceId}/participants/search?q=` staff search by bib prefix or name (20 results, no contact/medical fields)
- Void: race managers can void any record; checkpoint staff can void only their own non-RFID entry within 5 minutes (`RECORD_UNDO_WINDOW_EXPIRED`). Wave start: assigned officials may start an unstarted wave at the current time only (`GUN_TIME_OFFICIAL_NOW_ONLY`); corrections remain admin-only.
- `POST /api/v1/races/{raceId}/checkpoints/{checkpointId}/records/{recordId}/void` `{reason}`
- `POST /api/v1/races/{raceId}/checkpoints/{checkpointId}/records/{recordId}/restore` `{reason}` (`TIMING_SLOT_OCCUPIED` if the athlete already has a time there)
- `POST /api/v1/races/{raceId}/checkpoints/{checkpointId}/records/{recordId}/reassign` `{participantId | participantBibNumber, reason}`

Voided records stay in `checkpoint_records` (`voided_at`, `voided_by`, `void_reason`) and are hidden from every entity query by `@SQLRestriction`; results, cutoffs, live tracking, and the participant status recompute without them. Their RFID reads move to the `VOIDED` status so regate/reprocess cannot recreate them, and each action writes a correction-history row and an audit event.

Timing records are refused (`PARTICIPANT_NOT_ENTERED`, HTTP 409; RFID events marked `ERROR`) for
pending-approval, waitlisted, and rejected registrations. When an official corrects a checkpoint
time, every cutoff for that participant is re-evaluated from the corrected start: a newly missed
cutoff marks them DNF, and a DNF caused only by a cutoff that no longer applies is reversed.

Live checkpoint submissions use the server receipt time and reject a client-supplied `recordedAt` with `CHECKPOINT_TIMESTAMP_NOT_ALLOWED`. The athlete self-scan route (`POST /api/v1/tracking/{raceId}/checkpoint`) also records the server receipt time and ignores any client timestamp. Authorized race admins and assigned officials may import historical timing CSV rows at `POST /api/v1/races/{raceId}/checkpoints/import`; volunteers cannot use that import route.

### RFID / Bib Timing Bridge
- `POST /api/v1/races/{raceId}/rfid/readers`
- `GET /api/v1/races/{raceId}/rfid/readers`
- `PATCH /api/v1/races/{raceId}/rfid/readers/{readerId}/revoke`
- `POST /api/v1/rfid/read-events`
- `POST /api/v1/rfid/readers/heartbeat`

Reader management routes require operator authentication. Creating a reader returns the plaintext `apiKey` only once; the database stores only its SHA-256 hash. Timing bridges call `POST /api/v1/rfid/read-events` and `POST /api/v1/rfid/readers/heartbeat` with `X-RFID-Reader-Key`.

RFID events whose normalization throws are retried with backoff (5 s doubling to 5 min) and marked `ERROR` after 8 attempts, so a failing event cannot crowd newer reads out of the recovery sweep.

RFID reads are stored immediately as raw `rfid_read_events` with a queued `RECEIVED` status, published to Redis Streams, and normalized asynchronously into official `checkpoint_records` with `recordMethod=RFID`. Participant matching now resolves through `participant_credentials`, with the legacy participant `rfidTag` field kept for backward compatibility and auto-synced to `RFID_UHF_TIMING` credentials. Reader heartbeat updates expose gateway status, backlog, firmware/model metadata, hardware profile metadata, and last-processed timestamps so demo compact-UHF readers and Impinj event-grade readers stay visible through the same inventory surface.

### Tracking
- `POST /api/v1/tracking/{raceId}/location`
- `POST /api/v1/tracking/{raceId}/locations`
- `POST /api/v1/tracking/{raceId}/checkpoint`
- `GET /api/v1/races/{raceId}/tracking/live`
- `GET /api/v1/races/{raceId}/tracking/{participantId}`

Location ingest rules:
- Uploads for one athlete are serialized with a transaction-scoped advisory lock, and each batch is sorted by `recordedAt` before drift filtering, so concurrent or out-of-order uploads cannot double-count distance.
- Points dated more than `racetracker.tracking.max-clock-skew-seconds` (default 120) in the future are stored as filtered (`FUTURE_TIMESTAMP`) and never become the reference point.
- Before the race is `IN_PROGRESS`, positions are stored and shown live but add no distance.
- Uploads for a `FINISHED`, `DNF`, `DNS`, or `DISQUALIFIED` entry, or a `COMPLETED`/`CANCELLED` race, return 409 `TRACKING_CLOSED`, and the mobile app stops tracking. Pending, waitlisted, or rejected entrants get 403.
- Athlete self-scans (`POST /tracking/{raceId}/checkpoint`) require the per-checkpoint secret from the printed QR code (`checkpointId:raceId:token`), an `IN_PROGRESS` race, and a non-start/finish checkpoint (`SELF_SCAN_CODE_INVALID`, `RACE_NOT_IN_PROGRESS`, `SELF_SCAN_NOT_ALLOWED`). The app sends its current fix with the scan; it goes through the normal GPS filters. A scan is recorded (200 `VERIFIED`) only when the latest accepted fix is at most `self-scan-max-fix-age-seconds` (120) old and within `checkpoint-radius-meters` plus the fix accuracy (capped at `self-scan-accuracy-allowance-meters`, 50). Otherwise it is stored as a pending `self_scan_claims` row (202 `PROVISIONAL`) that never affects status or results.
- Staff (`SUPER_ADMIN`, `RACE_ADMIN`, assigned `OFFICIAL`) get the QR code at `GET /races/{raceId}/checkpoints/{checkpointId}/self-scan-qr`, list open claims at `GET /races/{raceId}/self-scan-claims`, and approve or reject them at `POST /races/{raceId}/self-scan-claims/{claimId}/decision`. Approval records the crossing at the scan time and applies normal progression and cutoffs. A claim for a checkpoint that official timing has since recorded closes as `SUPERSEDED`. Race admins rotate a leaked code with `POST .../self-scan-qr/rotate`. Every decision and rotation is audited.
- Live WebSocket updates are published only after the transaction commits. The staff live endpoint uses batched queries and returns `recordedAt` (time of the last accepted fix) so stale athletes are visible.

### Results integrity
- `POST /api/v1/races/{raceId}/results/publish` creates an immutable results version (SHA-256 of the canonical results, chained to the previous version; stored copy has no personal data). Database triggers refuse edits and deletes of published versions and of time-correction history.
- `POST .../results/certify` makes the latest version official. It is refused while timing changed since publish (`RESULTS_CHANGED_SINCE_PUBLISH`), the published table no longer matches its fingerprint (`RESULTS_INTEGRITY_FAILED`), or athlete queries are open (`RESULT_QUERIES_OPEN`). Certification locks every write that affects results (`RESULTS_CERTIFIED`) until `POST .../results/reopen` with a reason.
- `GET .../results/integrity` recomputes the hashes; `GET .../results/versions` lists releases; `GET .../results/anomalies` flags missing timed-mat splits, implausible segment speeds (`racetracker.tracking.max-plausible-split-speed-kmh`, default 30), and out-of-order crossings.
- Time corrections require a reason and keep append-only history; status decisions require a reason; penalties require a reason; checkpoints and participants with timing data cannot be deleted; only draft races can be deleted.
- Athletes query their result at `POST /api/v1/results/my/{raceId}/queries`; officials answer at `POST /api/v1/races/{raceId}/result-queries/{queryId}/resolution` (athlete notified, audited).
- Waves: `/api/v1/races/{raceId}/waves` (create, start/correct gun with reason, assign participants). Gun precedence: wave gun → wave schedule → race gun → race schedule. Gun corrections re-gate automatic RFID times.
- Laps: checkpoints carry `expectedPasses`, `minPassSeconds`, and `firstPassIsStart`; records carry `passNumber`. Only the final pass at a finish checkpoint finishes.
- Staff captures may include `capturedOfflineAt` (≤ 24 h old, not future, not before the athlete's gun); they are noted and audited.

### Public Spectator Tracking
- `GET /api/v1/public/races/{raceId}/tracking/athletes/{participantId}/track?since=<ISO-8601>` returns only points recorded after `since` (used by the mobile live view for incremental refresh); without `since` it returns the whole downsampled track. Athletes with tracking disabled always return no points.
- `GET /api/v1/public/races/{raceId}/route` is cacheable for 5 minutes.
- `GET /api/v1/public/races/{raceId}/hub`
- `GET /api/v1/public/races/{raceId}/tracking/live`
- `GET /api/v1/public/races/{raceId}/tracking/search?q=<term>`
- `GET /api/v1/public/races/{raceId}/tracking/athletes/{participantId}`

Anonymous access is expected for `GET` requests under `/api/v1/public/races/**` only. Existing admin, organizer, and athlete routes remain protected by the normal security rules.

Public payloads must stay spectator-safe. Include only race summary, leaderboard, split, and athlete tracking fields needed for public viewing, and do not return email addresses, phone numbers, emergency contacts, medical notes, device tokens, internal notes, or other admin-only audit data.

### Results

Timing start is the participant's earliest START checkpoint record (chip start), otherwise the
race's scheduled gun start (`raceDate` + `startTime` in the race `timezone`). Registration and
desk check-in instants are never used as a start. Chip time is measured from that start; gun
time from the scheduled start. Every timing path (RFID, staff manual/QR, timing import, athlete
self-scan) applies the same status transition: a crossing starts a registered/checked-in
participant, a FINISH crossing marks them finished, a missed cutoff marks them DNF, and
FINISHED/DNF/DNS/DISQUALIFIED decisions are never overwritten by a later read.

- `GET /api/v1/races/{raceId}/results`
- `GET /api/v1/races/{raceId}/results/{participantId}`
- `GET /api/v1/results/my`
- `POST /api/v1/races/{raceId}/results/publish`

### Dashboard and Notifications
- `GET /api/v1/dashboard/stats`
- `GET /api/v1/notifications`
- `PUT /api/v1/notifications/{id}/read`

## WebSocket (Live Tracking)

STOMP endpoint (also exposed with a SockJS fallback):
- `ws://<host>/api/ws-tracking` (the contract value `web.wsTrackingPath`)

The STOMP `CONNECT` frame must carry `Authorization: Bearer <access-token>`; unauthenticated
connections are rejected. Subscriptions are authorized per destination:

- `/topic/race.{raceId}` — live location updates for race staff only (super admins, the race's
  admins, and officials/volunteers assigned to that race). Spectators use the public REST feed.
- `/topic/user.{userId}` — the authenticated user's own alerts; other users' topics are rejected.

Each race-topic message is a `LiveTrackingResponse` (participant id, bib, name, smoothed
latitude/longitude, speed, status, last checkpoint, elapsed time, pace, splits). Only GPS points
accepted by the drift filter are broadcast.

## API Documentation
Swagger UI:
- `/api/swagger-ui/index.html`

OpenAPI JSON:
- `/api/v3/api-docs`

## Tests
```bash
cd apps/backend
mvn clean test
```

The `FlywayPostgresMigrationSmokeTest` also migrates a clean PostGIS database
through the latest version when `FLYWAY_TEST_URL`, `FLYWAY_TEST_USERNAME`, and
`FLYWAY_TEST_PASSWORD` are set. Without those variables it is skipped. GitHub
Actions provides a disposable PostGIS service for this gate.

For pilot deployment, use the repository-root `compose.pilot.yml` and
`.env.pilot.example`; production PostgreSQL/PostGIS remains externalizable. Read
`docs/pilot-operations-runbook.md` before baseline adoption, backup, restore, or
rollback. A non-empty database must be structurally compared to the V1 baseline
before it is manually baselined; this repository does not claim that any existing
environment has already passed that comparison.

Current test suite includes controller-level smoke tests under:
- `src/test/java/com/racetracker/controller`

Focused operational tests:
```bash
cd apps/backend
mvn -Dtest=ActuatorSecurityIntegrationTest,TrackingObservationCoverageTest test
```
