# Open decisions and implementation notes

Items that are blocked on an external decision or a dedicated effort. Each entry records the
current state, the decision needed, and the concrete steps to finish.

## 1. SMS / WhatsApp providers

**State.** `NotificationDispatcher` + `NotificationChannel` adapters exist; `SmsNotificationChannel`
and `WhatsAppNotificationChannel` are config-gated placeholders (`racetracker.notifications.sms-enabled`,
`racetracker.notifications.whatsapp-enabled`) that log and drop when enabled without a provider.

**Decision.** Provider choice — Twilio, Africa's Talking (strong ZA/Zim coverage), or Meta
WhatsApp Cloud API.

**Steps.**
1. Add the provider SDK (or a `RestClient`-based client) under `service/impl`.
2. Implement `send(...)` in the two channels using `NotificationMessage.recipientPhone`.
3. Add credentials to `RaceTrackerProperties.Notifications` + `.env.pilot.example` +
   `ProductionConfigurationValidator` (require config when enabled).
4. Keep delivery audit (`notification_deliveries`) — statuses already recorded.

## 2. Object storage (S3 / MinIO)

**State.** `MediaStorage` interface with `LocalMediaStorage`; `racetracker.storage.provider`
(default `local`) is the selection seam.

**Decision.** AWS S3 vs MinIO vs another S3-compatible store.

**Steps.**
1. Add `software.amazon.awssdk:s3` to `apps/backend/pom.xml`.
2. Implement `S3MediaStorage` (`store`/`storeBytes`/`load`/`delete`); return object keys (or
   full URLs) consistently with `MediaServiceImpl.publicUrl`.
3. Select the bean by `racetracker.storage.provider=s3` (`@ConditionalOnProperty`); add bucket,
   region, endpoint, and credentials to config + production validation.
4. Serve via presigned URLs or a CDN; update `MediaController` if content is redirected.

## 3. Apple OAuth

**State.** Google OAuth is implemented via tokeninfo (`GoogleOAuthIdentityVerifier`); privileged
accounts are rejected from OAuth sessions (must use verified-email password authentication).

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
supplied reader key per minute. Heartbeat remains unthrottled. Production Redis enforcement
and metrics still require deployment/runtime verification.

**Evidence.** `RequestRateLimiterTest`, `OAuthControllerWebMvcTest`, and
`RfidControllerWebMvcTest` cover the policies and service short-circuit behavior. The current
implementation uses the trusted-proxy-aware client address resolver and hashes identities
before creating Redis keys.

## 6. Client i18n

**Decided & in progress.** Languages fixed at `en` (default), `sn`, `nd`, `af`, `zu`, `fr`,
`pt`, `sw` (no RTL). Foundation (Phase 0) delivered; web extraction (Phase 1) in progress.
See `docs/PENDING_WORK.md` §1 for the full phase status and remaining areas.
