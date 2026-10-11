# P1 Product-Depth Implementation Plan (2026-10-11)

Implements the P1 scope from `docs/IMPLEMENTATION_GAP_ANALYSIS_2026_10_11.md`
(P1 product depth) plus the open P0 follow-ups that belong to the same areas.
Extends `docs/GAP_ANALYSIS_2026_10_09.md`.

Baseline: migration head `V20`, contract `apiVersion: 1`
(`apps/backend/src/main/resources/application.yml:188`), `race_results` table
exists but is unused by Java (`V1__baseline.sql:381-409`).

## Conventions (apply to every phase)

- Flyway is the only schema source; append `V21+`; never edit an applied migration.
- Controller (HTTP) -> service (logic) -> repository (data); DTO + MapStruct only;
  never expose entities; explicit `@Transactional`; domain exceptions
  (`ApiException`/`ResourceNotFoundException`/`ConflictException`/`AccessDeniedException`).
- Contract is the source of truth: change `packages/contracts/contract.json` +
  `racetracker.api.version` + `index.js`/`index.d.ts` + web/mobile clients +
  `validate-contracts.mjs` together, then run `pnpm contracts:validate`.
- Public `/api/v1/public/races/**` stays anonymous and PII-free.
- Every phase ends with the CI verify set (below).

## Phase 1 — Timing correctness & result persistence (P1 #1; C5 follow-up)

Goal: enforce cutoffs, verify race completion, persist official results.

- DB `V21__timing_verification.sql`: `races.completed_at TIMESTAMPTZ`;
  `checkpoint_records.is_cutoff_missed BOOLEAN DEFAULT false` + query index.
  No `race_results` schema change (reuse existing columns).
- Cutoff enforcement: `CheckpointServiceImpl.record` (`:119-141`) compares
  effective time vs `Checkpoint.cutoffTime` (`Checkpoint.java:55`); on miss flag
  the record and transition participant to `DNF` without deleting timing data.
  Mirror in `TrackingServiceImpl` (`:155-156`) and `RfidReadEventProcessorImpl`
  (`:142-152`).
- Race completion: validated status state machine in `RaceServiceImpl.updateStatus`
  (`:198-205`); scheduled `IN_PROGRESS` -> `COMPLETED` close with auto-DNF for
  non-finishers (`@EnableScheduling` already on). `ResultServiceImpl.publish`
  (`:155-164`) refuses until completion verified; set `completed_at`.
- Results persistence: new `RaceResult` entity + repository over the existing
  `race_results` table; `publish` snapshots computed results (`gun_time`,
  `chip_time`, positions, pace/speed, flags) via upsert; reads serve persisted
  rows under the existing revision guard. Remove dead
  `ResultServiceImpl.assignPositions` (`:319-349`).
- Tests: `CheckpointServiceImplCutoffTest`, `RaceCompletionTest`,
  `ResultPersistenceTest`. Verify `mvn -B test`.

## Phase 2 — Unified notification dispatcher + mobile push + event alerts (P1 #2; C8/C9)

Goal: one dispatcher with channel adapters; event-driven triggers; push delivery.

- DB `V22__notifications.sql`: `notification_templates`,
  `notification_preferences`, `notification_deliveries` (retry/audit).
- Backend: `NotificationDispatcher` + `NotificationChannel` adapters
  (`EmailChannel` wrapping `RegistrationEmailService`/`AuthEmailService`,
  `PushChannel`, `SmsChannel`, `WhatsAppChannel` behind config flags). Replace
  imperative triggers with `ApplicationEventPublisher` + `@TransactionalEventListener`
  (no eventing exists today). Route `SpectatorAlertServiceImpl` (`:68-90`) through
  the dispatcher so it pushes. Add hooks: payment status (`PaymentServiceImpl.applyStatus:165-201`),
  check-in (`ParticipantServiceImpl:173-185`), RFID checkpoints
  (`RfidReadEventProcessorImpl:99-138`), result publish. Fix `data` nulling
  (`NotificationServiceImpl:146`).
- Mobile: register FCM/Expo token via `POST /notifications/devices`, add
  `setNotificationHandler` + listeners, implement the Notifications screen
  (placeholder `app/(tabs)/profile.tsx:93`).
- Web: add `firebase-messaging-sw.js` + SW registration and notification center;
  add `VITE_FIREBASE_*` to `.env.example`.
- Contract: notification-template admin endpoints. Tests for dispatcher and
  channel selection.

## Phase 3 — Real-time spectator web + pace charts (P1 #3)

Goal: live-updating public pages and a numeric pace/split chart.

- Real-time: public pages are one-shot + manual refresh
  (`PublicRaceHubPage.tsx:62-156`, `PublicAthleteTrackingPage.tsx:98-117`).
  Adopt `swr` (installed, unused) to interval-poll the revision-cursor
  `live-feed` (`PublicTrackingServiceImpl:382-421`); evaluate a public SSE
  endpoint if polling is insufficient. Do not expose auth-gated STOMP topics to
  anonymous clients.
- Backend: extend `CheckpointSplitDto` (`:12-14`) with numeric
  `distanceMeters`, `durationSeconds`, `paceSecondsPerKm` (contract change).
- Web: recharts pace/split chart on `PublicAthleteTrackingPage` (replace text
  list `:305-329`); resolve WS paths from the contract in `connectUserAlerts`/
  `connectLiveTracking` (`api.ts:758`).
- Contract + validate; web lint/typecheck/test/build.

## Phase 4 — Analytics/checkpoint exports + device-quality trends (P1 #5)

Goal: streaming exports and device-quality analytics.

- Exports: stream results via `SXSSFWorkbook` + opencsv streaming
  (`ResultExportServiceImpl:65-159` is in-memory/XSSF today); add checkpoint
  export and analytics export (csv/xlsx); keep participant CSV admin-only (PII).
- Device-quality: heartbeat time-series/per-reader trend endpoint over
  `RfidRaceOverviewDto` data (`RfidReaderServiceImpl:102-138`) + web view.
- Contract for new export keys; tests proving no full-list load.

## Phase 5 — Media storage abstraction + thumbnails (P1 #6)

Goal: pluggable storage and image derivatives.

- Add `MediaStorage` interface; move concrete `FileStorageService` behind
  `LocalMediaStorage`; add `S3MediaStorage` (config/profile-selected); give
  `delete()` the same path-traversal guard as `store()`/`load()`.
- DB `V23__media_derivatives.sql`: `race_media.thumbnail_key`, `width`, `height`;
  generate thumbnails on upload; define thumbnail + public URL strategy.
- Config keys + provider tests (S3 mocked).

## Phase 6 — Payment provider strategy + automated refunds (P1 #7; C7 follow-up)

Goal: pluggable providers and real refunds.

- `PaymentProvider` strategy keyed on `Payment.provider` (`Payment.java:33`);
  extract Pesepay into `PesepayPaymentProvider`; add refund/reversal to
  `PesepayClient` (only `initiate`/`checkStatus` exist).
- DB `V24__payment_refunds.sql`: `payment_refunds` (idempotency, amount, reason,
  status). Replace the `refund()` hard-fail (`PaymentServiceImpl:117-131`) with a
  provider call + local record; reconcile manual `verifyPayment`
  (`ParticipantServiceImpl:511-534`) with the `payments` table and promo counts;
  run the audit event only on success.
- Contract if new refund-status fields; tests incl. idempotency.

## Phase 7 — Mobile Emergency SOS (P1 #4; depends on Phase 2)

Goal: end-to-end SOS with location broadcast.

- DB `V25__emergency_alerts.sql`: `emergency_alerts` (participant, race, lat/lng,
  status, timestamps).
- Backend: `POST /races/{raceId}/emergency` (authenticated athlete) -> persist +
  high-priority dispatch to race admins + emergency contacts via the Phase 2
  dispatcher. No public exposure.
- Mobile: SOS button/screen on `app/tracking.tsx` using existing location +
  offline queue; Emergency Contact screen (placeholder `profile.tsx:92`).
- Contract + tests.

## Phase 8 — Remaining P1 breadth

Independent slices, each with contract + verify:
- Excel import (POI alongside CSV).
- OAuth (Google/Apple).
- i18n (`MessageSource` + web/mobile).
- Route/GPX geometry (race route + map rendering).

## Sequencing

1 -> 2 -> 3, with 4/5 parallelizable after 1; 7 requires 2; 8 after 1-6 as capacity
allows. Bump `apiVersion` once per phase that adds endpoints.

## Verify (matches CI)

`pnpm contracts:validate`; `pnpm --filter web lint && typecheck && test && build`;
`pnpm --dir apps/mobile lint`; `pnpm --dir apps/mobile exec tsc --noEmit`;
`pnpm --dir apps/mobile test`; `pnpm --filter rfid-gateway test`; backend
`mvn -B test`; reader adapters `mvn -B test` in `apps/rfid-reader-demo` and
`apps/rfid-reader-impinj`.

## Open decisions (resolve before the relevant phase)

1. SMS/WhatsApp provider for Phase 2 (Twilio, Africa's Talking, Meta WhatsApp Cloud).
2. Object storage for Phase 5 (AWS S3 vs MinIO/other).
3. OAuth provider/order for Phase 8.
4. Contract version strategy: per-phase bump vs. single 2.0 at the end.

## Status

### Phase 1 — done (2026-10-11)

`V21__timing_verification.sql`; cutoff enforcement in the manual, GPS, and RFID record
paths (`is_cutoff_missed` + DNF); race completion state machine + auto-complete scheduler
(`races.completed_at`); results snapshotted into `race_results` on publish with persisted
reads; dead `assignPositions` removed. Backend `mvn -B test` 175 passing.

### Phase 2 — core delivered (2026-10-11)

`V22__notification_deliveries.sql`; unified `NotificationDispatcher` with channel
adapters (`push`/`email` enabled by existing config; `sms`/`whatsapp` placeholders behind
`racetracker.notifications.*`); after-commit fan-out with per-channel delivery audit;
WebSocket user alerts now emitted for all notifications; `NotificationDto.data` no longer
nulled; new hooks for check-in, payment success/failure, and RFID spectator alerts; mobile
push-token registration + Notifications screen; web `firebase-messaging-sw.js` + SW
registration + `VITE_FIREBASE_*` template. Backend `mvn -B test` 177 passing; web
lint/typecheck/test/build; mobile lint/tsc/test; gateway test; contracts validate.

Deferred from Phase 2 (require a decision or contract change):
- `notification_preferences` (opt-out API) and `notification_templates` (admin CRUD) —
  new endpoints would bump `apiVersion` across clients; needs product copy/i18n decisions.
- Real SMS/WhatsApp providers (open decision 1); adapters are config-gated placeholders.
- Result-publish / race-completed fan-out — O(participants) per publish; wants an async,
  batched event path first.
- Web notification-center UI (background push service worker is in place).

### Phase 3 — delivered (2026-10-11)

`CheckpointSplitDto` now carries numeric `distanceMeters`, `durationSeconds`, and
`paceSecondsPerKm` (public athlete tracking and authenticated live responses); the public
athlete page renders a recharts pace-by-split area chart. Public hub and athlete pages
adopt `swr` with a 15s `refreshInterval` and `revalidateOnMount: false` when loader data is
present (no duplicate initial transport calls); `connectUserAlerts` resolves the WebSocket
path from the contract. No `contract.json` change was required (contract lists endpoint
paths, not response shapes). Backend `mvn -B test` 178 passing; web
lint/typecheck/test/build; contracts validate.

Note: chose SWR interval polling over a public SSE endpoint; revisit SSE only if polling
proves inadequate at scale (auth-gated STOMP topics remain closed to anonymous clients).

### Phase 4 — delivered (2026-10-11)

`TabularExportWriter` (opencsv + streaming `SXSSFWorkbook`) backs results (PDF/CSV/XLSX),
new checkpoint export (`/races/:raceId/checkpoints/export`) and analytics export
(`/races/:raceId/analytics/export`, multi-sheet). Device-quality: `RfidRaceOverviewDto`
gained a per-reader breakdown and an hourly event trend (native aggregation) plus web
types/API. Contract keys `raceCheckpointExport`/`raceAnalyticsExport` added (apiVersion
unchanged — additive). Backend tests 181; web verified.

### Phase 5 — delivered (2026-10-11)

`MediaStorage` abstraction + `LocalMediaStorage` (replaces the concrete `FileStorageService`,
`delete()` now path-guarded); `MediaServiceImpl` depends on the interface. JDK `ImageIO`
thumbnails (`ImageThumbnailService`), `V23__media_derivatives.sql` (`thumbnail_key`,
`width`, `height`), thumbnail endpoints (staff + public), DTO/type + gallery preview
support. S3 backend remains gated on the object-storage decision (open decision 2);
`racetracker.storage.provider` is the selection seam.

### Phase 6 — delivered (2026-10-11)

`PaymentProvider` strategy (resolved by `Payment.provider`) + `PesepayPaymentProvider`;
`PesepayClient.refund(...)` (path configurable via `racetracker.payments.refund-path`);
`V24__payment_refunds.sql` + `PaymentRefund` record; `PaymentServiceImpl.refund` now calls
the provider, is idempotent, updates payment/participant to REFUNDED, records the refund,
and notifies the athlete. Controller accepts an optional amount/reason body.

### Phase 7 — delivered (2026-10-11)

`V25__emergency_alerts.sql` + `EmergencyAlert`; `POST /races/:raceId/emergency` (athlete)
persists and alerts race staff via the dispatcher; staff list endpoint. Mobile SOS button
on the tracking screen (confirm dialog, current location) + `api.reportEmergency`; contract
key `raceEmergency`.

### Phase 8 — delivered (2026-10-11)

- **Excel import**: `processImportAsync` now reads `.xlsx`/`.xls` via POI `WorkbookFactory`
  (plus CSV) through a shared `readImportRows` path.
- **OAuth**: `POST /auth/oauth/google` verifies Google ID tokens via tokeninfo
  (`OAuthIdentityVerifier`), links/creates an ATHLETE account, and issues a session;
  privileged accounts are rejected (must use password + MFA). Apple deferred pending
  provider credentials/JWKS work.
- **i18n**: `messages.properties` + `LocalizedMessages` wrapper over Spring `MessageSource`.
- **Route/GPX**: `V26__race_route_points.sql`; `POST /races/:raceId/route/gpx` parses GPX
  (secured XML parser), staff + public `GET .../route`; contract keys `raceRoute`,
  `raceRouteGpx`, `publicRaceRoute`.

Backend `mvn -B test` 191 passing; contracts validate; web lint/typecheck/test/build;
mobile lint/tsc/test.

Deferred in Phase 8: Apple OAuth, non-English catalogs/client i18n, GPX/route map
rendering in web/mobile (API + storage are in place), and Excel-import preview UI.

## Pending-work closure (2026-10-11)

Implemented the open backlog on top of Phases 1–8:

- **C2 authorization depth**: `RaceAccessGuard` (GREEN: per-race ownership) applied to the
  participant, checkpoint, and RFID controllers; `RaceServiceImpl.list` now scopes RACE_ADMIN
  results to owned/assigned races via `RaceRepository.search`.
- **CSV import durability**: `ParticipantImportProcessor` (separate bean so `@Async` applies)
  with `import_jobs.source_path`/`attempts`, bounded retry, and a scheduled recovery that
  resumes stuck jobs.
- **Notifications**: `V28` adds `notification_preferences` + `notification_templates`;
  preferences API on `/notifications/preferences`, admin template CRUD on
  `/admin/notification-templates`, and the dispatcher now honors per-channel opt-outs.
- **AI insights**: `RaceInsightsService` + `/races/:raceId/insights/summary|anomalies`
  (leader finish projection, drift-filtered GPS / missed-cutoff / DQ anomaly scoring).
- **GDPR**: `V29` adds consent + `deleted_at`; `/me/data` export, `/me/consent`, and
  `DELETE /me` anonymization.
- **gps_locations**: `V30` retention index + `GpsRetentionService` (age-based purge behind
  `racetracker.tracking.retention-days`). Range partitioning remains an ops migration.
- **Ops/e2e/docs**: `deploy/k8s/*`, `deploy/monitoring/prometheus-alerts.yml`,
  `.github/workflows/deploy.yml`, `tests/load/tracking-load.js`, `tests/e2e/*`,
  `docs/architecture.md`, `docs/er-diagram.md`.
- **Frontend/coverage**: web `lib/map-provider.ts` abstraction (Mapbox + MapLibre fallback)
  used by the three map views; `CheckpointServiceImplTest`.

Verification: backend `mvn -B test` 213 passing; `contracts:validate`; web
lint/typecheck/test/build; mobile lint/tsc/test; gateway test.

Still requiring an external decision or dedicated effort (no code gap in-repo):
- Real **SMS/WhatsApp** providers and **S3** object storage (decisions 1–2) — adapters/seams exist.
- **Apple OAuth** (needs Apple credentials/JWKS validation).
- Full **gps_locations partitioning** (table rewrite in a maintenance window).
- Spectator/admin **mobile apps** and richer client i18n.
- E2E Cypress/load suites are scaffolded but not wired into CI (no pinned deps run).




