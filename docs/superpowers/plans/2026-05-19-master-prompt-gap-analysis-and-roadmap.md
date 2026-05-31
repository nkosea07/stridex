# Master Prompt Gap Analysis And Roadmap (2026-05-19)

Source specification: `marathon_race_tracking_master_prompt.md`

## Status Legend
- `DONE`: broadly implemented and usable
- `PARTIAL`: implemented in part; important gaps remain
- `MISSING`: not implemented in product code

## Evidence Baseline (Current Implementation)
- Backend API/security/controllers:
  - `apps/backend/src/main/java/com/racetracker/config/SecurityConfig.java`
  - `apps/backend/src/main/java/com/racetracker/controller/AuthController.java`
  - `apps/backend/src/main/java/com/racetracker/controller/RaceController.java`
  - `apps/backend/src/main/java/com/racetracker/controller/RaceParticipantController.java`
  - `apps/backend/src/main/java/com/racetracker/controller/CheckpointController.java`
  - `apps/backend/src/main/java/com/racetracker/controller/TrackingController.java`
  - `apps/backend/src/main/java/com/racetracker/controller/ResultController.java`
  - `apps/backend/src/main/java/com/racetracker/controller/DashboardController.java`
- Backend auth/domain/config:
  - `apps/backend/src/main/java/com/racetracker/model/User.java`
  - `apps/backend/src/main/java/com/racetracker/security/JwtService.java`
  - `apps/backend/src/main/java/com/racetracker/service/impl/AuthServiceImpl.java`
  - `apps/backend/src/main/resources/application.yml`
  - `apps/backend/src/main/java/com/racetracker/config/DataInitializer.java`
- Web:
  - `apps/web/src/App.tsx`
  - `apps/web/src/components/auth/RequireAdminRoute.tsx`
  - `apps/web/src/pages/*.tsx`
- Mobile:
  - `apps/mobile/app/(auth)/*.tsx`
  - `apps/mobile/app/(tabs)/*.tsx`
  - `apps/mobile/app/tracking.tsx`
  - `apps/mobile/store/tracking.ts`
- Infra/tests:
  - `apps/backend/docker-compose.yml`
  - `apps/backend/pom.xml`
  - `apps/backend/src/test/java/com/racetracker/**/*.java`

## Module-By-Module Gap Matrix

### 1. Authentication & User Management
Status: `PARTIAL`
- Implemented:
  - JWT access + refresh token flow.
  - Login/register/current user/profile update endpoints.
  - Role checks for admin web route.
- Gaps:
  - Prompt roles not fully modeled (`OFFICIAL`, `ATHLETE`, `SPECTATOR` missing).
  - No OAuth/Google/Apple login.
  - No MFA, password reset, email verification flow, account lockout, security audit trail.

### 2. Race Management
Status: `PARTIAL`
- Implemented:
  - Create/list/get/update/delete races.
  - Status updates.
  - Categories and checkpoints modeled.
- Gaps:
  - Clone/archive workflow not explicit.
  - Branding/media/poster pipeline not implemented.
  - Multi-stage/multi-day orchestration and timezone depth are limited.

### 3. Participant Registration System
Status: `PARTIAL`
- Implemented:
  - Self-registration + race registration/cancellation.
  - Participant CRUD and CSV-assisted onboarding.
  - Bib number field support.
- Gaps:
  - Dynamic forms, waiver/consent workflows, medical emergency workflow depth.
  - Team/club registration workflows are basic or absent.
  - Payment-linked approval workflows absent.

### 4. CSV Import / Export
Status: `PARTIAL`
- Implemented:
  - CSV participant import/export and result export capability.
- Gaps:
  - Background processing queue, robust preview/error-report UX, and very large-file handling.
  - Export coverage for analytics/checkpoints not complete.

### 5. Printable Bib / Race Number Generation
Status: `PARTIAL`
- Implemented:
  - Bib fields and QR-related dependencies/config.
- Gaps:
  - No complete bib PDF generation + template/batch printing pipeline.
  - Barcode/emergency branding layout generation not productized.

### 6. Live Athlete Tracking
Status: `PARTIAL`
- Implemented:
  - GPS location ingest (single + batch), checkpoint tracking, live endpoint.
  - WebSocket support and map views on web/mobile.
- Gaps:
  - Robust offline buffering/retry strategy is limited.
  - GPS drift correction, heat maps, route playback, spectator follow/favorites incomplete.
  - True background tracking hardening is incomplete.

### 7. Checkpoint & Timing
Status: `PARTIAL`
- Implemented:
  - Checkpoint CRUD and timing records.
  - Mobile QR scanner flow.
- Gaps:
  - Penalties, DNF/DNS, correction workflows, official timing console depth.
  - RFID integration implementation (readiness only).

### 8. Results Management
Status: `PARTIAL`
- Implemented:
  - Result list/detail, publish endpoint, basic ranking fields.
- Gaps:
  - Certificates, medals, qualification logic, historical records, personal bests.
  - Expanded ranking dimensions (team/country) not fully implemented.

### 9. Payment System
Status: `MISSING`
- Implemented:
  - No integrated payment gateways detected in product code.
- Gaps:
  - Stripe/PayPal/Flutterwave/Paynow/EcoCash.
  - Refunds, coupons, promo, tiered pricing.

### 10. Email & Notification System
Status: `PARTIAL`
- Implemented:
  - In-app notification entities/endpoints.
  - Mail config scaffolding and templates/log tables in schema.
- Gaps:
  - Email sending flows are not active in runtime path.
  - SMS/push/WhatsApp integrations missing.
  - Multi-language template management missing.

### 11. Analytics & Reporting
Status: `PARTIAL`
- Implemented:
  - Dashboard stats endpoint and core admin dashboard.
  - CSV export present.
- Gaps:
  - Rich BI dashboards, PDF/Excel reporting, predictive analytics/AI insights.

### 12. Media Module
Status: `MISSING`
- Implemented:
  - No full media management module found.
- Gaps:
  - Uploads, galleries, athlete matching, moderation, social sharing workflow.

### 13. Mobile Application Features
Status: `PARTIAL`
- Implemented:
  - Auth, race browsing, registration, tracking, QR scanner, results/profile tabs.
- Gaps:
  - Spectator-focused app workflows are limited.
  - Admin incident/timing operations are limited.
  - Push notifications/background robustness/offline-first behavior need expansion.

### 14. Security Requirements
Status: `PARTIAL`
- Implemented:
  - JWT, hashed passwords, authenticated API baseline, role-guarding for some routes.
- Gaps:
  - CSRF policy is globally disabled (acceptable for strict stateless JWT APIs but still requires explicit posture documentation).
  - No visible rate limiting/API throttling implementation.
  - Endpoint-level authorization consistency is incomplete beyond race controller.
  - GDPR/privacy controls and audit logs incomplete.

### 15. Performance & Scalability
Status: `PARTIAL`
- Implemented:
  - WebSocket + PostgreSQL + containerized local stack.
- Gaps:
  - Redis used as container only; no cache integration in app code.
  - No queue/event-driven backbone in implementation.
  - No explicit load/performance benchmarks in repo.

### 16. DevOps & Deployment
Status: `PARTIAL`
- Implemented:
  - Docker compose for local dependencies; environment-profile split.
- Gaps:
  - CI/CD pipelines, Kubernetes manifests, full monitoring/alerting/backup runbooks.
  - Cloud deployment automation not found.

### 17. Testing
Status: `PARTIAL`
- Implemented:
  - Backend has focused tests for auth/security/race/tracking/data seeding.
- Gaps:
  - Web/mobile automated test suites are largely absent.
  - No load/security testing suite in repo.

### 18. Documentation
Status: `PARTIAL`
- Implemented:
  - Core READMEs and implementation plans exist.
  - OpenAPI dependency is present.
- Gaps:
  - End-to-end architecture docs, ER diagrams, admin/user manuals, deployment playbooks incomplete.

### 19. UI/UX Requirements
Status: `PARTIAL`
- Implemented:
  - Responsive admin web shell and map-enabled pages.
- Gaps:
  - Broader accessibility pass, dark mode parity, richer map analytics visuals.

### 20. AI-Powered Features
Status: `MISSING`
- Implemented:
  - No AI feature set detected in product runtime.
- Gaps:
  - Predictions, anomaly/fraud detection, smart pacing, summaries/chat assistant.

---

## Prioritized Execution Backlog

### P0 (Critical: Security + Correct Access + Core Reliability)
- [ ] `P0-01` Expand role model and policy matrix:
  - Add explicit roles required by prompt (`ATHLETE`, `SPECTATOR`, `OFFICIAL`) and codify permissions.
- [ ] `P0-02` Enforce endpoint authorization consistency:
  - Add method-level guards on all sensitive controller operations.
  - Add tests for deny/allow matrices per role.
- [ ] `P0-03` Production-safe auth hardening:
  - Password reset flow.
  - Email verification flow.
  - Account lockout/rate-limited login protection.
- [ ] `P0-04` Environment correctness:
  - Validate `JWT_SECRET` strength at startup with actionable failure message.
  - Align DB credential defaults between compose and app env docs.
- [ ] `P0-05` Non-admin experience split:
  - Keep admin dashboard admin-only.
  - Add user dashboard and direct non-admin users away from admin routes.

### P1 (Core Product Completeness)
- [ ] `P1-01` Registration workflow depth:
  - Dynamic forms, waivers/consents, emergency/medical structured capture.
  - Team/club registration workflows.
- [ ] `P1-02` Timing/results completeness:
  - DNF/DNS/penalties/corrections.
  - Certificate generation and richer ranking slices.
- [ ] `P1-03` Communication pipeline:
  - Transactional email sender integration behind feature flag.
  - Template management + delivery logs operationalized.
- [ ] `P1-04` Tracking robustness:
  - Offline queue/retry for location + checkpoint events.
  - Background tracking strategy refinement and telemetry.
- [ ] `P1-05` Reporting upgrades:
  - Exportable PDF/Excel reports.
  - Expanded dashboard metrics.

### P2 (Scale + Enterprise Extensions)
- [ ] `P2-01` Payment integrations:
  - Start with one gateway adapter and abstraction for additional providers.
- [ ] `P2-02` Redis + queue/event architecture:
  - Introduce cache layer for hot read endpoints.
  - Queue long-running jobs (CSV, notifications, media processing).
- [ ] `P2-03` Media module:
  - Asset storage, moderation, athlete-media association.
- [ ] `P2-04` AI feature wave:
  - Baseline anomaly detection for GPS and simple finish prediction.
- [ ] `P2-05` DevOps hardening:
  - CI/CD, monitoring, alerts, backup/restore drills, load testing.

---

## Suggested Implementation Phases

### Phase 1 (1-2 weeks)
- Deliver all `P0` tasks.
- Exit criteria:
  - Non-admin cannot access admin routes.
  - Auth flows stable in local/dev/prod profiles.
  - Endpoint authorization tests pass.

### Phase 2 (2-4 weeks)
- Deliver `P1-01` through `P1-04`.
- Exit criteria:
  - End-to-end participant journey is complete and resilient.
  - Core operations (registration, timing, tracking, notifications) are production-ready.

### Phase 3 (3-6 weeks)
- Deliver `P1-05` + `P2` tracks.
- Exit criteria:
  - Enterprise reporting, scale features, and commercialization modules are in place.

---

## Immediate Next Actions (Recommended)
- [ ] Convert `P0` items into tracked tickets (one ticket per checklist line).
- [ ] Add explicit acceptance criteria + owner per ticket.
- [ ] Run security-focused regression after each `P0` merge.
