# Open decisions and implementation notes

Items that are blocked on an external decision or a dedicated effort. Each entry records the
current state, the decision needed, and the concrete steps to finish.

## 1. SMS / WhatsApp providers

**Decided.** WhatsApp uses the Meta Cloud API. SMS is disabled for this release.

**State.** `WhatsAppCloudClient` sends approved template messages, signed webhooks update
`notification_deliveries`, and production requires the WhatsApp credentials when enabled.
`SmsNotificationChannel` is permanently disabled, production validation rejects enabling SMS,
and the web settings surface marks SMS unavailable.

**Remaining.** Approve the Meta templates per event/locale and run staging delivery evidence.

## 2. Object storage (S3 / MinIO)

**Implemented.** `MediaStorage` has `LocalMediaStorage` (development only), `AwsS3MediaStorage`,
and `MinioMediaStorage`, selected by `racetracker.storage.provider` (`STORAGE_PROVIDER`). The
production validator rejects local storage and requires a private bucket plus credentials.
Media is served through the backend (`no-store`), not public bucket URLs.

**Remaining.** Run the AWS S3 and MinIO provider smoke tests in staging and record the evidence
(`docs/RELEASE_READINESS.md`, Media storage gate).

## 3. Apple OAuth

**State.** Deferred for this release: the `prod` profile disables OAuth and the production
validator rejects enabling it. Google OAuth (`GoogleOAuthIdentityVerifier`, tokeninfo) remains
for development and test; privileged accounts are rejected from OAuth sessions.

**Decision.** Apple Services ID / team / key credentials.

**Steps.**
1. Add an Apple verifier implementing `OAuthIdentityVerifier` that validates the identity token
   against Apple's JWKS (`https://appleid.apple.com/auth/keys`) including `iss`, `aud`, and
   `exp`; cache the keys.
2. Add `POST /auth/oauth/apple` to `OAuthController` and wire `OAuthAccountService`.
3. Handle the private relay email and `email_verified` semantics.

## 4. `gps_locations` partitioning

**State.** `GpsRetentionService` (age-based purge) + `idx_gps_locations_recorded_at` (V30) exist.
The table is **not** range-partitioned.

**Decision.** Maintenance window + partitioning cadence (monthly).

**Steps (ops migration).**
1. Create a partitioned parent `gps_locations` by `RANGE (recorded_at)` and monthly child
   partitions; recreate indexes/geography triggers per partition.
2. Backfill from the existing table in batches; swap names in a transaction.
3. Add a scheduled job to create future partitions and drop expired ones (align with
   `racetracker.tracking.retention-days`).
4. Validate with `FlywayPostgresMigrationSmokeTest` on a PostGIS instance before release.

## 5. Public-endpoint rate limiting

**State. Done in code.** Google OAuth is limited to 10 requests per client IP per 15 minutes.
RFID read ingest is limited to 120 requests per client IP per minute and 600 requests per
supplied reader key per minute. Heartbeat remains unthrottled. Public spectator endpoints are
limited per race to 120 requests per minute for each signed-in user or app install
(`X-Race-Tracker-Client`), under a 1200 per minute ceiling per IP address so phones behind
carrier NAT are not blocked together; both are configurable. Production Redis enforcement
and metrics still require deployment/runtime verification.

**Evidence.** `RequestRateLimiterTest`, `OAuthControllerWebMvcTest`, and
`RfidControllerWebMvcTest` and `PublicRequestLimiterTest` cover the policies and service short-circuit behavior. The current
implementation uses the trusted-proxy-aware client address resolver and hashes identities
before creating Redis keys.

## 6. Client i18n

**Decided & in progress.** Languages fixed at `en` (default), `sn`, `nd`, `af`, `zu`, `fr`,
`pt`, `sw` (no RTL). Foundation (Phase 0) delivered; web extraction (Phase 1) in progress.
See `docs/PENDING_WORK.md` §1 for the full phase status and remaining areas.
