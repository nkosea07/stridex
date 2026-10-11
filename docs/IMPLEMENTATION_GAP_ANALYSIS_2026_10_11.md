# AMS / StrideX Implementation Gap Analysis (2026-10-11)

Code-level audit of the committed implementation against
`marathon_race_tracking_master_prompt.md` (20 modules).

Extends/supersedes `docs/GAP_ANALYSIS_2026_10_09.md`: that document was a
capability-level audit; this one verifies each spec item against actual code with
`file:line` evidence and separates **verified** findings from audit-reported ones.

Method: parallel read-only audits of backend (25 controllers, 36 services, 31 repos,
19 migrations, 48 tests), web (25 routes), mobile, and cross-cutting concerns, followed
by independent verification of every Critical/High claim. Legend: **Done** = usable
end-to-end · **Partial** = foundation exists, spec incomplete · **Missing** = not
implemented.

## Executive summary

The platform is pilot-grade and broad (auth+MFA, RFID ingest via Redis Streams,
geospatial telemetry, spectator web, registration workflows, results/certificates,
media, analytics, Pesepay payments). The gaps are **depth and hardening**, not breadth:

1. **Correctness/security landmines** that break under non-dev config or leak data.
2. **Scale-out gaps** for the 10k–50k target (GPS volume, WS broker, notification
   fan-out, in-memory exports).
3. **Unbuilt spec modules**: AI (20), Emergency SOS, spectator/admin mobile apps,
   SMS/WhatsApp, GDPR, deploy/CD, e2e/load tests.

### Verified critical/high findings

| # | Finding | Evidence | Impact |
|---|---|---|---|
| C1 | **`import_jobs` table has no Flyway migration.** `ImportJob` maps `import_jobs`; zero matches in `db/migration`. | `model/ImportJob.java:12`; grep of `db/migration` → none; `ddl-auto: validate` (`application.yml:35`) | CSV import fails at startup/runtime outside the `dev` profile. The 2026-10-09 "persisted ImportJob" claim only holds with `ddl-auto: update`. |
| C2 | **No per-race authorization scoping.** `Race.createdBy` is never set on create and `race_admins` is never populated. | no `setCreatedBy` in `service/impl/RaceServiceImpl.java` (only `ParticipantServiceImpl.java:208`, `PenaltyServiceImpl.java:59` set it on other entities) | Any `RACE_ADMIN` can read/edit/clone/archive/delete any race and its participants. |
| C3 | **Internal exception text leaked to clients.** | `exception/GlobalExceptionHandler.java:140` (`"An unexpected error occurred: " + ex.getMessage()`) | Info disclosure across every endpoint. |
| C4 | **WebSocket SUBSCRIBE is not authorized.** Interceptor only handles `CONNECT`; endpoint is `permitAll`. | `config/WebSocketConfig.java:52-57`; `config/SecurityConfig.java:78` (`/ws/**`) | Any connected client can subscribe to any `/topic/race.{id}`, bypassing HTTP rate limiting. |
| C5 | **Results recomputed per request and silently truncated.** `race_results` table is unused; `listByRace` caps at 100. | `service/impl/ResultServiceImpl.java:71,163-199`; `V1__baseline.sql:381` | Large races show wrong/partial results; wasted CPU. |
| C6 | **`gps_locations` is not partitioned.** Only a deferred note exists. | `V1__baseline.sql:204-205` | Unbounded table growth at 50k athletes. |
| C7 | **Refunds are local-only.** `refund()` flips status without calling the provider. | `service/impl/PaymentServiceImpl.java:117-133` | "Refunded" participants are not actually refunded. |
| C8 | **Live/checkpoint/finish alerts never push or email.** Only in-app + WS. | `service/impl/SpectatorAlertServiceImpl.java:82-88` | Followers with the app closed miss all live alerts. |
| C9 | **Mobile push is not wired.** Backend FCM exists; mobile only sets a local channel. | `mobile/lib/notifications.ts:1-22`; backend `V16__push_devices.sql`, `FcmPushNotificationService` | No push notifications despite backend support. |
| C10 | **Contract drift not gated.** `wsTrackingPath` says `/api/ws/tracking`; server serves `/ws-tracking`. | `packages/contracts/contract.json:6` vs `config/WebSocketConfig.java:42` | Spectator contract is wrong; `contracts:validate` doesn't catch it. |

Also high: CSV import `@Async` is bypassed by self-invocation
(`service/impl/ParticipantServiceImpl.java:215,238`) and duplicate detection is absent
(`participants.email` is not unique — `V1__baseline.sql:160`); race completion/cutoff
verification is unimplemented (`CheckpointServiceImpl` stores `cutoffTime`, never
enforces it); the entire AI module (20) is missing; there is no deploy/CD stage.

## Module status matrix

| # | Module | Status | Headline gaps |
|---|---|---|---|
| 1 | Auth & User Mgmt | Partial | No OAuth/Google/Apple; no session-list/revoke-all; audit misses non-privileged logins; MFA forced on volunteers/officials |
| 2 | Race Management | Partial | No routes/geometry, sponsors, posters, multi-day, multi-stage, or timezone handling; cancel lacks reason/refund; **no per-race authz (C2)** |
| 3 | Registration | Partial | No automatic bib allocation; no human athlete ID; medical info absent from self-service; no QR check-in; import bypasses async (C1-related) |
| 4 | CSV Import/Export | Partial | **`import_jobs` unmigrated (C1)**; no duplicate detection or preview; no Excel import; no checkpoint/analytics export |
| 5 | Printable Bibs | Partial | No 1D barcode, sponsor branding, category coloring, or templates |
| 6 | Live Tracking | Done | Mapbox-only; no spectator live map/pace charts; N+1 live read; **GPS not partitioned (C6)**; **WS SUBSCRIBE unauth (C4)** |
| 7 | Checkpoint & Timing | Partial | No cutoff/completion verification; QR payload not decoded; DNF/DNS only enums; dead `assignPositions` |
| 8 | Results | Partial | No qualification, personal bests, or cross-race history; medals only top-3 overall; **recomputed/truncated (C5)** |
| 9 | Payments | Partial | Only Pesepay; **local-only refunds (C7)**; no team/early-bird pricing; promo-code race condition |
| 10 | Email & Notifications | Partial | No SMS/WhatsApp/emergency; **alerts don't push (C8)**; no payment confirmation, reminders, template mgmt, or i18n |
| 11 | Analytics & Reporting | Partial | No checkpoint/device-quality analytics; exports only for results |
| 12 | Media | Partial | No photo matching, facial recognition, or social sharing; local FS storage; response-header hardening |
| 13 | Mobile | Partial | No Emergency SOS, spectator app, or admin app; **push not wired (C9)**; no WS client; route is checkpoint-only |
| 14 | Security | Partial | **Error-text leak (C3)**; no global API throttle; no GDPR; CORS header wildcard; no public-GPS consent |
| 15 | Performance & Scale | Partial | **No GPS partitioning (C6)**; caching barely used; single-instance STOMP broker; no CDN/lazy loading |
| 16 | DevOps | Partial | No deploy/CD stage, k8s/IaC, alerting, or backup automation |
| 17 | Testing | Partial | No e2e (Cypress), load, or Postman; mobile has 1 test |
| 18 | Documentation | Partial | No annotated OpenAPI, architecture/ER diagrams, or user/admin manuals |
| 19 | UI/UX | Partial | Spectator live not real-time; a11y thin; dark mode partial on public pages; no Redux/Zustand |
| 20 | AI Features | Missing | Only a heuristic ETA; no predictions/forecasting/fraud/chatbot |

## Cross-cutting themes

- **Authorization depth.** Race-scoped RBAC is largely absent (C2); WebSocket destinations
  unguarded (C4); RFID/payment public endpoints rely only on shared-secret headers with no
  rate limiting or replay protection.
- **Durability of long-running work.** Only RFID has a durable stream
  (`RfidStreamWorker`); CSV import uses fire-and-forget `@Async` (self-invoked, so
  synchronous) with no queue/retry.
- **Scale.** GPS persistence 1:1 without partitioning/retention; N+1 live reads; synchronous
  per-follower notification fan-out; in-memory exports/results capped at 100.
- **Notifications.** In-app + email only; no push wiring to mobile, no SMS/WhatsApp, no
  event-driven hooks on payment/checkpoint/finish.
- **Privacy/GDPR.** Spectator DTOs are PII-free, but full name + near-real-time GPS is
  exposed without a per-athlete consent flag; no erasure/export/retention flows.
- **Contract integrity.** `wsTrackingPath` drift shows `contracts:validate` is incomplete.
- **Spec stack divergences (accepted).** Mobile is React Native/Expo (permitted alternative
  to Flutter); web is React Router 7 framework mode, not plain React+Redux; no GraphQL;
  no cloud provider chosen.

## Refactoring plan

- **CSV import**: add `V20__import_jobs.sql` (+ per-row error table); move import to a
  dedicated worker/queue with retry; add duplicate pre-check and a preview endpoint.
- **Results**: materialize into the existing `race_results` table on publish; add
  qualification, personal-best, and history endpoints; stream exports (SXSSF).
- **Payments**: introduce a `PaymentProvider` strategy keyed off `Payment.provider`; wire
  provider refunds with amount/reason; lock promo usage (`SELECT … FOR UPDATE` or a DB
  constraint); store minor-unit `long`.
- **Notifications**: one `NotificationDispatcher` with channel adapters (email/SMS/push/
  WhatsApp); `notification_templates` table + admin CRUD; event hooks on payment/checkpoint/
  finish; i18n `MessageSource`.
- **Tracking read model**: replace `getLiveData` N+1 with a projection; add a
  provider-agnostic `MapProvider` (MapLibre fallback for Google/OSM).
- **Frontend**: adopt Zustand for live-tracking state and SWR (already a dependency, unused)
  for REST caching; centralize the STOMP singleton into a hook; remove the duplicate
  `DashboardLayout`.
- **Media**: abstract `FileStorageService` behind a `MediaStorage` interface (local + S3)
  and add thumbnails; harden public content headers.
- **Dead code**: remove `ResultServiceImpl.assignPositions`/overloads, unused
  `refresh_tokens` schema, and duplicate `toFieldDto`.

## Implementation roadmap

- **P0 — correctness & security (do first).** Migrate `import_jobs` (C1); enforce per-race
  authorization and fix `Race.createdBy` (C2); stop error-text leakage (C3); add WebSocket
  destination authorization (C4); persistent results/pagination fix (C5); wire provider
  refunds or disable the feature (C7); promo-code concurrency lock; CSV duplicate detection
  + working async; reconcile `wsTrackingPath` and extend `contracts:validate` (C10).
- **P1 — product depth.** Race completion/cutoff verification; persist results; unified
  notification dispatcher + mobile push wiring (C9) + payment/finish/checkpoint alerts (C8);
  spectator live web (WS/polling) + pace charts; mobile Emergency SOS; analytics/participant/
  checkpoint exports; analytics checkpoint/device-quality views; media storage abstraction.
- **P2 — enterprise/AI/scale.** `gps_locations` partitioning + retention (C6); event-driven
  scale (external WS relay, import queue); AI `insights` bounded context (predictions,
  forecasting, fraud/suspicious-GPS); GDPR export/erase/consent; OpenAPI annotations +
  architecture/ER diagrams; k8s/IaC + deploy/CD + alerting + backups; Cypress/e2e + load
  tests; map-provider abstraction; spectator/admin mobile apps.

## Confidence & scope notes

- Critical/High items C1–C10 were independently verified in code during this audit.
- Remaining module detail comes from parallel read-only audits and is evidence-linked but
  not individually re-verified; treat as high-confidence directional findings.
- Out of scope: product-strategy, pricing, and marketing; see `docs/plans/` for those.

## Remediation status (2026-10-11)

P0 fixes implemented and verified against the full CI set (contracts; web lint/test/build;
mobile lint/tsc/test; gateway test; backend `mvn -B test` — 161 tests).

| Item | Status | Change |
|---|---|---|
| C1 — `import_jobs` unmigrated | **Done** | Added `V20__import_jobs.sql` matching the `ImportJob` entity. |
| C2 — no per-race authz | **Done (scoped)** | `Race.createdBy` set on create/clone; `assertCanManage` guards update/delete/status/archive/restore/clone; unowned legacy races stay manageable; added a 403 handler. Follow-up: apply scoping to participant/checkpoint/RFID controllers and filter the admin race list. |
| C3 — error-text leak | **Done** | `GlobalExceptionHandler` no longer returns `ex.getMessage()`; logs with correlation id and returns a support-reference message. |
| C4 — WS SUBSCRIBE unauth | **Done** | STOMP interceptor now requires an authenticated `CONNECT` and restricts `SUBSCRIBE` to `/topic/**` + `/queue/**`. |
| C5 — truncated results | **Done (truncation)** | `listByRace`/`getMyResults` now iterate all pages instead of capping at 100. Follow-up: persist into the existing `race_results` table. |
| C7 — fake refunds | **Done (disabled)** | `refund()` refuses with a clear error instead of marking payments/participants REFUNDED without a provider call. Follow-up: wire the Pesepay refund API. |
| C10 — contract drift | **Done** | `wsTrackingPath` corrected to `/api/ws-tracking`; web client resolves it from the contract; `contracts:validate` now asserts the path across contract, backend, and web. |
| Promo-code race | **Done** | Atomic conditional `UPDATE ... usedCount` guard (`incrementUsedCountIfAvailable`). |
| CSV duplicate detection | **Done** | Import preloads existing emails and skips duplicates within-file and against the race. |

Still open (P1/P2, not in this P0 pass): CSV import async self-invocation; payment provider
strategy; notification dispatcher (SMS/push wiring); GPS partitioning; results persistence;
AI module; GDPR; e2e/load tests; deploy/CD.
