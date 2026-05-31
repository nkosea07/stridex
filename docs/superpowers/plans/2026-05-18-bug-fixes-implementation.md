# Bug Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore build stability, fix broken authorization and API contracts, and close high-risk auth/security gaps.

**Architecture:** Fix in dependency order: first unblock backend compilation, then enforce correct role and permission mapping, then align API contracts across backend/web/mobile, then harden token handling, then gate unsafe seed behavior by environment. Keep changes small and test-backed.

**Tech Stack:** Spring Boot 3, Spring Security, JWT (jjwt), React (Vite), React Native (Expo), TypeScript, Maven, pnpm.

---

### Task 1: Restore Backend Compilation

**Files:**
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/AuthServiceImpl.java`
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/CheckpointServiceImpl.java`
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/DashboardServiceImpl.java`
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/NotificationServiceImpl.java`
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/ParticipantServiceImpl.java`
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/RaceServiceImpl.java`
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/ResultServiceImpl.java`
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/TrackingServiceImpl.java`

- [ ] Add missing imports for each implemented service interface.
- [ ] Add missing import for `RaceCategoryRequest` in `RaceServiceImpl`.
- [ ] Run: `mvn -q -DskipTests test-compile` (from `apps/backend`).
- [ ] Commit: `fix(backend): restore service impl compilation by adding missing imports`.

### Task 2: Fix Role/Authorization Mismatch

**Files:**
- Modify: `apps/backend/src/main/java/com/racetracker/controller/RaceController.java`
- Create: `apps/backend/src/test/java/com/racetracker/controller/RaceControllerSecurityTest.java`

- [ ] Replace invalid role checks (`ADMIN` and `USER`) with actual roles (`SUPER_ADMIN`, `RACE_ADMIN`, `VOLUNTEER`) per intended access matrix.
- [ ] Add security-focused MVC tests for allowed and forbidden role scenarios on read/write race endpoints.
- [ ] Run: `mvn -q -Dtest=RaceControllerSecurityTest test`.
- [ ] Commit: `fix(security): align race endpoint role guards with domain roles`.

### Task 3: Align Race List API Contract (Page vs Array)

**Files:**
- Modify: `apps/backend/src/main/java/com/racetracker/controller/RaceController.java` or client files below (choose one canonical contract and apply everywhere)
- Modify: `apps/web/src/lib/api.ts`
- Modify: `apps/web/src/pages/RacesPage.tsx`
- Modify: `apps/web/src/pages/DashboardPage.tsx`
- Modify: `apps/mobile/lib/api.ts`
- Modify: `apps/mobile/app/(tabs)/index.tsx`
- Modify: `apps/mobile/app/(tabs)/races.tsx`
- Modify: `apps/mobile/app/(tabs)/my-races.tsx`
- Modify: `apps/mobile/app/(tabs)/results.tsx`

- [ ] Decide contract: `Page<RaceDto>` or `RaceDto[]`.
- [ ] Update all web/mobile callsites and types to consume that contract consistently.
- [ ] Add/update API-client unit tests for response parsing.
- [ ] Run web checks: `pnpm --filter web lint` and `pnpm --filter web build`.
- [ ] Commit: `fix(api): unify race list response contract across backend and clients`.

### Task 4: Harden Access/Refresh Token Semantics

**Files:**
- Modify: `apps/backend/src/main/java/com/racetracker/security/JwtService.java`
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/AuthServiceImpl.java`
- Modify: `apps/backend/src/main/java/com/racetracker/security/JwtAuthenticationFilter.java`
- Modify/Add tests under: `apps/backend/src/test/java/com/racetracker`

- [ ] Add token-type claim (for example `typ=access|refresh`) during token issuance.
- [ ] Reject refresh tokens in auth filter for normal API authorization.
- [ ] Validate refresh endpoint accepts only refresh token type.
- [ ] Add tests: access token cannot refresh and refresh token cannot authenticate normal endpoint.
- [ ] Run targeted tests, then `mvn -q test`.
- [ ] Commit: `fix(auth): enforce token type separation for access and refresh flows`.

### Task 5: Remove Unsafe Default Admin Seeding in Non-Dev

**Files:**
- Modify: `apps/backend/src/main/java/com/racetracker/config/DataInitializer.java`
- Modify: `apps/backend/src/main/resources/application.yml`

- [ ] Gate seeding behind explicit property and/or `dev` profile only.
- [ ] Default behavior must not create known credentials in production-like environments.
- [ ] Add config test for seed-enabled vs seed-disabled behavior.
- [ ] Commit: `fix(security): disable default admin bootstrap outside dev`.

### Task 6: Full Regression Verification

**Files:**
- No product code required; test/report updates only if needed.

- [ ] Run backend: `mvn -q test`.
- [ ] Run web: `pnpm --filter web lint && pnpm --filter web build`.
- [ ] Run mobile lint/tests if configured.
- [ ] Smoke test critical flows: login, race listing, participant listing, live tracking, refresh token endpoint.
- [ ] Commit: `chore: verify and stabilize post-fix regressions`.
