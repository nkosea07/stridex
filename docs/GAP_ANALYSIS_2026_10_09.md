# AMS / StrideX Gap Analysis (2026-10-09)

Audit of the committed codebase against `marathon_race_tracking_master_prompt.md`.
Supersedes `docs/GAP_ANALYSIS_2026_05_31.md` (now stale).

Legend: **Done** = usable end-to-end · **Partial** = foundation exists, spec incomplete · **Missing** = no implementation.

## Executive summary

The platform is pilot-grade: strong auth (MFA, browser sessions, lockout), a durable
RFID ingest pipeline (Redis Streams), geospatial telemetry, a public spectator web
surface, registration workflows (fields/approvals/waitlist/teams), audit logging, and a
CI + container pipeline. Remaining gaps cluster in **commercialization** (payments,
media), **communication** (push/SMS), **reporting** (exports, analytics, certificates),
and **enterprise/AI**.

## Module status

| # | Module | Status | Notes / gaps |
|---|---|---|---|
| 1 | Auth & User Mgmt | **Done** | Roles, JWT+refresh, Secure-cookie sessions, MFA (TOTP + recovery), email verify, password reset, lockout, audit, rate limiting. Missing: Google/Apple OAuth, session-management UI, GDPR |
| 2 | Race Management | **Done** | CRUD, categories, checkpoints incl. water/medical types, statuses, clone/archive/restore (P0-1). Missing: sponsors, route/GPX, multi-stage/day, timezone |
| 3 | Registration | **Done** | Self + admin, dynamic fields, waiver, emergency contact, approval, waitlist, teams, credentials, bib allocation, online + manual payment |
| 4 | CSV Import/Export | **Partial** | Async CSV import (persisted `ImportJob`), CSV export. Missing: Excel import, durable queue, preview UI |
| 5 | Printable Bibs | **Partial** | iText PDF, ZXing QR, batch. Missing: 1D barcode, sponsor branding, templates |
| 6 | Live Tracking | **Done** | GPS ingest, drift filter, playback, heatmap, live feed, leaderboard, favorites, WebSocket |
| 7 | Checkpoint & Timing | **Done** | CRUD + record, QR scan, manual entry, splits, auto-ranking, DNF/DNS/DQ, penalties + time corrections (P0-2) |
| 8 | Results | **Partial** | Rankings (overall/gender/category/age-group), medals, team/country rankings, revisions, public results, CSV/PDF/XLSX export, certificates. Missing: personal bests, qualification status |
| 9 | Payments | **Partial** | Pesepay (EcoCash/cards/Zimswitch) online checkout + webhook + manual verify (P0-5). Missing: refunds, coupons, promo/early-bird |
| 10 | Email & Notifications | **Partial** | Transactional email + in-app + FCM push backend (P0-4). Missing: SMS, WhatsApp, template management UI |
| 11 | Analytics & Reporting | **Partial** | Basic dashboard + telemetry tray + heatmap. Missing: revenue/demographics/completion analytics, BI |
| 12 | Media | **Partial** | Image/video upload, race gallery, athlete tagging, moderation, public approved gallery (P1). Missing: AI facial matching, social sharing |
| 13 | Mobile Features | **Partial** | Athlete app: registration, background GPS, offline queue, results, scanner. Missing: SOS, push wiring, spectator, timing-official app |
| 14 | Security | **Done** | JWT hardening, Secure cookies, MFA, rate limiting, append-only audit, correlation IDs, prod validator, hashed RFID keys, PII-safe public endpoints |
| 15 | Performance & Scale | **Partial** | Redis cache/streams, pagination, PostGIS GiST indexes. Missing: `gps_locations` partitioning, CDN, load testing |
| 16 | DevOps & Deployment | **Partial** | Dockerfiles, CI, pilot compose, Nginx, actuator, runbooks. Missing: Kubernetes/IaC, backup automation, alerting |
| 17 | Testing | **Partial/Good** | 149 backend tests, web/mobile/gateway suites, CI. Missing: E2E, load/security testing |
| 18 | Documentation | **Partial** | README, AGENTS, contract, runbooks, plans. Missing: live OpenAPI, diagrams, manuals |
| 19 | UI/UX | **Good** | Tailwind, dark mode, responsive dashboards, public spectator UX. Missing: a11y audit, i18n |
| 20 | AI Features | **Missing** | Basic predicted finish only |

## Priority roadmap

- **P0 — commercialization (delivered 2026-10-09):** race clone/archive, checkpoint
  penalties + time corrections, results exports + certificates, FCM push, Pesepay payments.
- **P1 — product depth:** age-group/team/country rankings + medals **(delivered 2026-10-09)**;
  media gallery with tagging + moderation **(delivered 2026-10-09)**; analytics/BI;
  Excel import; OAuth; i18n; route/GPX geometry; refunds/coupons.
- **P2 — enterprise/AI:** AI anomaly + forecasting; Kubernetes/IaC; load/security/E2E
  testing; GDPR erasure/export; mobile spectator + SOS; SMS/WhatsApp.
