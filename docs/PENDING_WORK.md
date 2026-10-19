# Pending Work Register (2026-10-12)

Single inventory of remaining gaps and phased work, consolidating
`docs/IMPLEMENTATION_GAP_ANALYSIS_2026_10_11.md`, `docs/GAP_ANALYSIS_2026_10_09.md`,
`docs/open-decisions.md`, `docs/backlog.md`, `docs/security-review.md`, the P1 plan
(`docs/plans/2026-10-11-p1-product-depth-implementation.md`), and the i18n effort.

Legend: **Done** · **Partial** · **Pending** · **Blocked** (needs a decision).

---

## 1. Internationalization (i18n)

Languages: `en` (default/fallback), `sn`, `nd`, `af`, `zu`, `fr`, `pt`, `sw`. No RTL yet.
Guaradirails: `pnpm i18n:check` (CI), `eslint-plugin-i18next/no-literal-string` (scoped),
format helpers, `AGENTS.md` rule. Use `t('<namespace>:<key>')`.

- **Phase 0 — foundation: Done** (`e6d9d87`). `contract.i18n`, `packages/i18n`, web/mobile
  providers + switchers, `src/lib/format.ts`, `i18n:check`/`i18n:report`, CI wiring, AGENTS rule.
- **Phase 1 — web extraction: Partial** (`a3cb968`). Done: `auth`, `register`, `nav` + the four
  migrated files. Remaining areas (extract to a namespace, then add the file(s) to the
  `no-literal-string` override list in `apps/web/eslint.config.js`):
  - admin: `ParticipantsPage`, `CheckpointsPage`, `ResultsPage`, `AnalyticsPage`,
    `RaceInsightsPage`, `MediaPage`, `PromoCodesPage`, `CategoriesPage`, `TeamsPage`,
    `RfidReadersPage`, `RaceDetailsPage`, `RacesPage`, `OrganizerApplicationsPage`,
    `SettingsPage`, `NotificationsPage`, dashboard shell components.
  - registration: `RegistrationFieldsPage`, `PublicRaceRegistrationPanel`.
  - tracking: `LiveTrackingPage`, `CommandBar`, `Sidebar`, `AnalyticsTray`, `AeroPanel`,
    `RunnerCard`, `FreshnessIndicator`, `SecurityAuditor`.
  - public: `PublicRaceHubPage`, `PublicAthleteTrackingPage`, `PublicLeaderboard`,
    `PublicAthleteSearch`, `PublicAthleteCard`, `PublicRaceHero`, `PublicRaceResultsPanel`,
    `RaceHeatmap`, `RaceMediaGallery`, `AthleteTrackPlayback`, `FollowButton`.
  - shared: toaster strings, dialog/empty states, client-side validation/error copy.
  - End state: `no-literal-string` = error repo-wide.
- **Phase 2 — mobile extraction: Pending.** Mirror the web namespaces; device locale + switcher
  already wired; add a mobile eslint override as areas migrate.
- **Phase 3 — backend: Pending.** `Accept-Language` → `LocaleContextHolder` (interceptor/
  resolver); `users.locale` migration; localize `AuthEmailService`/`RegistrationEmailService`/
  `NotificationService` via `MessageSource` + `messages_<lang>.properties`; add stable
  `ErrorResponse.code` (+ `GlobalExceptionHandler`) and map codes → localized client strings.
- **Phase 4 — languages: Pending.** Seed `sn/nd/af/zu/fr/pt/sw` catalogs (English placeholders),
  translate guided by `pnpm i18n:report`. Adding a language = one catalog + the contract list.

## 2. Audit remediation (2026-10-11 audit)

- **Section 1 — backend capability → client surface: Done** (`c7f154a`), plus admin CRUD UIs and
  route rendering (`1657677`). Note: web Google OAuth button still missing (needs Google Identity
  Services + `VITE_GOOGLE_CLIENT_ID`); live-athlete list and route import/rendering partly wired.
- **Section 2 — no-op / third-party integration logic: Done** (`388b774`): SMS/WhatsApp honest
  status, FCM token pruning, templates applied, mobile push listeners + unregister, Impinj
  `isConnected`, gateway `eventOrigin`, RFID error metric, MapLibre fallback removed.
- **Section 3 — deceptive UI: Done** (`388b774`): settings copy, no-op dashboard controls,
  random sparkline, phone seeding.
- **Section 4 — contract / doc drift: Pending.**
  - Many implemented controllers are absent from `packages/contracts/contract.json` (auth/MFA/
    browser, admin, dashboard, registrations, participant/checkpoint CRUD, tracking admin,
    results publish, media content, RFID bridge); clients call literal paths.
  - `scripts/validate-contracts.mjs` does not compare controllers ↔ contract (drift ungated).
  - READMEs stale (`apps/backend/README.md` "later phases" + API surface list).
- **Section 5 — dead code: Pending.**
  - backend: unused `GeoTelemetryService` injection (`TrackingServiceImpl`),
    `ResultComputation.finishSeconds`, duplicate `toFieldDto` (`RaceServiceImpl`/
    `ParticipantServiceImpl`), unused tables `refresh_tokens`/`email_templates`/`email_logs`,
    `checkpoint_volunteers` model with no API.
  - web: leftover `App.tsx`/`main.tsx`/`index.html`, duplicate `components/ui/buttons.tsx`,
    unused deps (`papaparse`, `react-hook-form`, `zod`, `cmdk`, `input-otp`), STOMP `console.log`.
  - mobile: unused deps (`swr`, `@rnmapbox/maps`, `expo-linking`, `expo-web-browser`,
    `expo-system-ui`), dead `checkpointRecords`/`estimatedFinishTime`/`getMobileRuntimeInfo`,
    register debug logging.
- **Section 6 — genuinely missing features: Pending.**
  - Results: personal bests, qualification status, cross-race history.
  - CSV **timing/checkpoint** imports (only participant import exists).
  - AI depth: predictions/forecasting/fraud (current insights are heuristic).
  - Printable bibs: 1D barcode, sponsor branding, templates.
  - Media: facial matching / social sharing (arguably AI scope).
  - Mobile parity: offline QR-checkpoint queue, results detail/certificate, favorites/spectator.

## 3. Product / enterprise (P2 from gap analysis)

- **`gps_locations` partitioning** — see §4 decision; retention + index already in place.
- **Event-driven scale** — external WebSocket relay, event bus; durable import queue is partly
  done (`import_jobs` + `ParticipantImportProcessor` recovery).
- **GDPR** — self-service export/consent/erase done; remaining: re-acknowledgement when the
  privacy policy version changes (B2 item 5), retention-exception documentation and
  query-based PII-absence tests.
- **Observability/docs** — `docs/architecture.md` + `docs/er-diagram.md` done; annotated OpenAPI
  and user/admin manuals pending.
- **Ops** — k8s manifests, deploy workflow and Prometheus alerts done; backup automation, full
  IaC, CDN and load/CDN tuning pending.

## 4. Decisions required (`docs/open-decisions.md`)

| # | Item | Status |
|---|---|---|
| 1 | SMS/WhatsApp provider (Twilio / Africa's Talking / Meta) | Blocked — placeholder logic |
| 2 | Object storage (S3 / MinIO) | Blocked — `MediaStorage` seam ready |
| 3 | Apple OAuth (Services ID/team/key) | Blocked — Google only |
| 4 | `gps_locations` partitioning (window + cadence) | Blocked — ops migration |
| 5 | Public-endpoint rate limiting (OAuth + RFID) | Done — OAuth 10/15m per IP; RFID 120/min per IP + 600/min per reader key; heartbeat excluded |
| 6 | Client i18n | Decided — see §1 |

## 5. Security residual risks (`docs/security-review.md`)

- Pesepay refund endpoint contract unverified against provider docs.
- Google verification depends on the remote tokeninfo call (no local JWKS cache).
- OAuth and RFID ingest throttling is implemented; production Redis enforcement and alerting
  still require deployment/runtime verification.
- Erase irreversibility is mitigated by the grace period + cancel path (done).

## 6. Environment / CI caveats

- New CI jobs `load-smoke` (k6) and `e2e` (Cypress) have **not run on GitHub Actions** yet.
- Docker image builds (CI `containers` job) not run locally.
- Migrations are validated locally on PostGIS **V1–V33**; nothing is pushed and no PR is open.

## 7. Suggested sequencing

1. **Finish i18n Phase 1** area-by-area (admin → registration → tracking → public → shared),
   then **Phase 2** (mobile), **Phase 3** (backend), **Phase 4** (locales).
2. **Push the branch and let CI run** (validates the new CI jobs, Docker builds, migration smoke).
3. Verify the deployed Redis-backed rate limits and metrics, then complete **§2 Section 5
   dead-code removal** (low risk).
4. Resolve **§4 #1–#4** decisions, then implement (SMS/WhatsApp, S3, Apple OAuth, partitioning).
5. **§4 Section 4 contract-drift** cleanup + extend `validate-contracts.mjs`; **§2 Section 6**
   product features (results depth, timing imports, AI depth, bibs, media).
