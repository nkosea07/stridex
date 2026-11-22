# Pending Work Register (updated 2026-11-22)

Single inventory of remaining gaps and phased work, consolidating
`docs/IMPLEMENTATION_GAP_ANALYSIS_2026_10_11.md`, `docs/GAP_ANALYSIS_2026_10_09.md`,
`docs/open-decisions.md`, `docs/backlog.md`, `docs/security-review.md`, the P1 plan
(`docs/plans/2026-10-11-p1-product-depth-implementation.md`), and the i18n effort.

Legend: **Done** · **Partial** · **Pending** · **Blocked** (needs a decision).

---

## 1. Internationalization (i18n)

Languages: `en` (default/fallback), `sn`, `nd`, `af`, `zu`, `fr`, `pt`, `sw`. No RTL yet.
Guardrails: `pnpm i18n:check` (CI), `eslint-plugin-i18next/no-literal-string` (scoped),
format helpers, `AGENTS.md` rule. Use `t('<namespace>:<key>')`.

- **Phase 0 — foundation: Done** (`e6d9d87`). `contract.i18n`, `packages/i18n`, web/mobile
  providers + switchers, `src/lib/format.ts`, `i18n:check`/`i18n:report`, CI wiring, AGENTS rule.
- **Phase 1 — web extraction: Partial** (`a3cb968`, `3292f01`). Most admin, registration and
  auth screens use `t()`, and the email-link pages (`/verify-email`, `/reset-password`,
  `/forgot-password`, PR #51) were built translated. Still hardcoded English as of 2026-11-22
  (extract to a namespace, then add the file to the `no-literal-string` override list in
  `apps/web/eslint.config.js`):
  - pages: `AnalyticsPage`, `RaceInsightsPage`, `PromoCodesPage`, `TeamsPage`,
    `NotificationsPage`, `LandingPage`, `PublicRaceHubPage`, `PublicAthleteTrackingPage`
    (`PrivacyPolicyPage`/`TermsPage` render the shared `legal` namespace).
  - components: `DashboardLayout`, `Sidebar`, `ThemeSelect`, `AnalyticsTray`, `AeroPanel`,
    `SecurityAuditor`, `PublicLeaderboard`, `PublicAthleteSearch`, `PublicAthleteCard`,
    `PublicRaceHero`, `PublicRaceResultsPanel`, `RaceHeatmap`, `RaceMediaGallery`,
    `AthleteTrackPlayback`.
  - End state: `no-literal-string` = error repo-wide; then load the non-English web catalogs
    (web currently uses only `sharedResources.en`).
- **Phase 2 — mobile extraction: Done** (`3292f01`). Every mobile screen uses `t('mobile:…')`.
- **Phase 3 — backend: Done** (`3292f01`, `V58__user_preferred_locale.sql`). Per-user
  `preferred_locale`, `Accept-Language` fallback (`UserLocales`), `MessageSource`-backed email and
  notification text (`LocalizedMessages`), stable API error codes mapped to client strings.
  Only `messages.properties` (English) exists; add `messages_<lang>.properties` as translations land.
- **Phase 4 — languages: Done for mobile** (PR #46). `sn/nd/af/zu/fr/pt/sw` catalogs for
  `mobile`, `account`, `legal` and the `common` keys mobile uses. Machine translated: needs
  native-speaker review (especially `sn`, `nd`, `zu`). Legal document bodies stay English with a
  translated notice. Web and backend catalogs are still English-only.

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
  - Mobile parity: **Done.** Operations timing, offline staff capture queue, certificates (including
    private races, PR #51), race-wide leaderboard/search (#45), photo gallery (#48), race map
    (#51) and in-app email-link screens (#51).
  - Race depth: timezone, stages, sponsors, cancellation reason, and bib branding added; advanced stage classifications remain pending.
  - PWA: manifest and offline shell added; install/offline device evidence pending.

## 3. Product / enterprise (P2 from gap analysis)

- **`gps_locations` partitioning** — see §4 decision; retention + index already in place.
- **Event-driven scale** — external WebSocket relay, event bus; durable import queue is partly
  done (`import_jobs` + `ParticipantImportProcessor` recovery).
- **GDPR** — self-service export/consent/erase and re-acceptance when the privacy policy or terms
  version changes are done (legal acceptance gate, web and mobile). Remaining: retention-exception
  documentation (`docs/backlog.md` item 5) and query-based PII-absence tests for every public
  endpoint (today only some public controller tests assert PII absence).
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
| 5 | Public-endpoint rate limiting | Done — OAuth 10/15m per IP; RFID 120/min per IP + 600/min per reader key (heartbeat excluded); public spectator endpoints 120/min per user or app install under a 1200/min per-IP ceiling (PR #51) |
| 6 | Client i18n | Decided — see §1 |

## 5. Security residual risks (`docs/security-review.md`)

- Pesepay refund endpoint contract unverified against provider docs.
- Google verification depends on the remote tokeninfo call (no local JWKS cache).
- OAuth, RFID and public spectator throttling are implemented; production Redis enforcement and
  alerting still require deployment/runtime verification.
- Public heatmap cells need at least `TRACKING_HEATMAP_MIN_PARTICIPANTS` (default 3, minimum 2)
  distinct athletes (PR #52). Comparing overlapping time windows can still reveal that an
  unnamed runner crossed a cell; fixed public time buckets would close this.
- Erase irreversibility is mitigated by the grace period + cancel path (done).

## 6. Environment / CI caveats

- As of 2026-11-22 every CI job runs on GitHub Actions (PRs #8, #30, #42, #50): `backend` (including the
  PostGIS Flyway smoke test for V1–V58), `javascript`, `load-smoke`, `containers` (Docker builds, a
  web image smoke test including app-link files, and Trivy image scans), `dependency-security`, and `deployment-preflight`. `e2e` (Cypress) is still
  skipped in CI.
- Accepted dependency advisories and their expiry dates are listed in
  `docs/RELEASE_READINESS.md` → *Dependency audit exceptions* and in `.trivyignore`.

## 7. Suggested sequencing

1. **§2 Section 5 dead-code removal** (low risk, in progress).
2. **Mobile release readiness** (§9): device testing, deployment values, translation review,
   store listings.
3. **Finish i18n Phase 1** (web) for the files listed in §1, then load web translations.
4. Verify the deployed Redis-backed rate limits and metrics.
5. Resolve the remaining **§4** decisions (Apple OAuth, partitioning) and capture S3/MinIO,
   WhatsApp and Pesepay refund provider evidence.
6. **Spring Boot 4 migration** before 2027-02-17 (§8).
7. **§2 Section 6** product features still pending (AI forecasting/fraud, facial matching,
   social sharing, advanced stage classifications) — each needs a product and privacy decision.

## 8. Dated security to-dos

| Due | Item | Status |
| --- | --- | --- |
| **2027-02-17** | **Migrate the backend to Spring Boot 4** (Spring Framework ≥ 7.0.9) and remove the CVE-2026-47884 / CVE-2026-47890 lines from `.trivyignore`. Plan: `docs/plans/2026-11-17-spring-boot-4-migration.md`. | **Pending** |
| **2027-05-16** | Re-review the accepted JavaScript advisories (`image-size` via Metro, `decode-uri-component` via React Navigation, `picomatch` via Expo CLI/Metro): upgrade Expo/React Navigation if fixed versions are compatible, otherwise renew with a new review date. `picomatch` (GHSA-fc5q-39xc-vjpf, PR #47) is only in `pnpm-workspace.yaml` `auditConfig.ignoreGhsas`, not `.trivyignore`, so the expiry workflow does not track it — check it manually. | **Pending** |

Reminders are automatic: `.github/workflows/security-exception-expiry.yml` runs every Monday and,
from 45 days before an expiry, opens (then comments weekly on) a GitHub issue labelled
`security-exception-expiry`, assigned to the repository owner. When an exception expires, Trivy
stops ignoring it and the `dependency-security`/`containers` CI jobs fail.

## 9. Mobile release readiness

The mobile app is feature-complete for the planned scope (see §2 Section 6). Before a store
release:

- **Device testing — Pending.** Nothing has run on a phone or simulator: maps, camera scanning,
  payment browser, push routing, background GPS, email links. There is no device test suite
  (Detox/Maestro).
- **Deployment values — Pending.** `APP_LINKS_IOS_APP_IDS`, `APP_LINKS_ANDROID_PACKAGE`,
  `APP_LINKS_ANDROID_SHA256_FINGERPRINTS` (`.env.pilot`) and `EXPO_PUBLIC_WEB_URL` (app build)
  for in-app email links; FCM credentials for push; `MOBILE_IOS_STORE_URL` /
  `MOBILE_ANDROID_STORE_URL` for the update screen.
- **Translation review — Pending.** Native-speaker review of all non-English mobile strings.
- **Store work — Pending.** Listings, screenshots, Apple privacy labels and Google data safety
  (location, install id, photos).
- **Known limitations (accepted):** videos open in the browser (no in-app player); the public
  media list is not paginated and media is served `no-store`.
