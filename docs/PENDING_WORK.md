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
- **Section 4 — contract / doc drift: Done** (`b7c737f` and the 2026-11-08 audit).
  - All controller routes are declared in `packages/contracts/contract.json`; both clients
    resolve paths from the contract.
  - `scripts/validate-contracts.mjs` fails on controller ↔ contract drift in either direction.
  - `docs/api-contract.md`, the backend README WebSocket/timing sections, and the RFID runbook
    were corrected in the 2026-11-08 audit.
- **Section 5 — dead code: Done** (2026-11-22). Removed: `ResultComputation.finishSeconds`, the
  duplicate `toFieldDto` (now `mapper/RegistrationFieldMappings`), tables `refresh_tokens`/
  `email_templates`/`email_logs` (`V59__drop_unused_legacy_tables.sql`); web `App.tsx`/`main.tsx`/
  `index.html`, `components/ui/buttons.tsx`, compiled `vite.config.js`/`.d.ts` (`tsc -b` now emits
  to `node_modules/.tmp`), deps `papaparse`/`@types/papaparse`/`react-hook-form`/`cmdk`/
  `input-otp`; mobile `swr`, `@rnmapbox/maps` (and its config plugin), `lib/mobile-runtime.ts`,
  `LiveStats.estimatedFinishTime`, web SQLite stub logging. Kept because they are in use:
  `GeoTelemetryService` (distance in `TrackingServiceImpl`), `checkpoint_volunteers`
  (`RaceStaffAssignmentRepository`), the `checkpointRecords` endpoint (staff recent entries),
  `expo-linking` (expo-router peer), `expo-system-ui` (`userInterfaceStyle`), `expo-web-browser`.
- **Section 6 — genuinely missing features: Partial.**
  - Results: personal bests, qualification status, and cross-race history endpoints/UI added.
  - CSV **timing/checkpoint** imports added; import preview and participant Excel export added.
  - AI depth: deterministic dropout-risk foundation added; forecasting/fraud/LLM remain pending.
  - Printable bibs: 1D barcode, sponsor branding, accent color, and template metadata added.
  - Media: facial matching and social sharing remain pending.
  - Mobile parity: operations timing mode added; offline QR queue and certificate detail remain pending.
  - Race depth: timezone, stages, sponsors, cancellation reason, and bib branding added; advanced stage classifications remain pending.
  - PWA: manifest and offline shell added; install/offline device evidence pending.

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
| 1 | SMS/WhatsApp provider | Decided — WhatsApp via Meta Cloud API; SMS disabled for this release |
| 2 | Object storage (S3 / MinIO) | Done in code — `AwsS3MediaStorage` / `MinioMediaStorage`; provider smoke evidence pending |
| 3 | Apple OAuth (Services ID/team/key) | Deferred — production disables OAuth; Google OAuth is dev/test only |
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

- As of 2026-11-17 every CI job runs on GitHub Actions (PRs #8, #30, #42): `backend` (including the
  PostGIS Flyway smoke test for V1–V58), `javascript`, `load-smoke`, `containers` (Docker builds +
  Trivy image scans), `dependency-security`, and `deployment-preflight`. `e2e` (Cypress) is still
  skipped in CI.
- Accepted dependency advisories and their expiry dates are listed in
  `docs/RELEASE_READINESS.md` → *Dependency audit exceptions* and in `.trivyignore`.

## 7. Suggested sequencing

1. **Finish i18n Phase 1** area-by-area (admin → registration → tracking → public → shared),
   then **Phase 2** (mobile), **Phase 3** (backend), **Phase 4** (locales).
2. **Push the branch and let CI run** (validates the new CI jobs, Docker builds, migration smoke).
3. Verify the deployed Redis-backed rate limits and metrics, then complete **§2 Section 5
   dead-code removal** (low risk).
4. Resolve the remaining **§4** decisions (Apple OAuth, partitioning) and capture S3/MinIO and
   WhatsApp provider evidence.
5. **§2 Section 6** product features (results depth, timing imports, AI depth, bibs, media).

## 8. Dated security to-dos

| Due | Item | Status |
| --- | --- | --- |
| **2027-02-17** | **Migrate the backend to Spring Boot 4** (Spring Framework ≥ 7.0.9) and remove the CVE-2026-47884 / CVE-2026-47890 lines from `.trivyignore`. Plan: `docs/plans/2026-11-17-spring-boot-4-migration.md`. | **Pending** |
| **2027-05-16** | Re-review the three accepted JavaScript advisories (`image-size` via Metro, `decode-uri-component` via React Navigation): upgrade Expo/React Navigation if fixed versions are compatible, otherwise renew with a new review date. | **Pending** |

Reminders are automatic: `.github/workflows/security-exception-expiry.yml` runs every Monday and,
from 45 days before an expiry, opens (then comments weekly on) a GitHub issue labelled
`security-exception-expiry`, assigned to the repository owner. When an exception expires, Trivy
stops ignoring it and the `dependency-security`/`containers` CI jobs fail.
