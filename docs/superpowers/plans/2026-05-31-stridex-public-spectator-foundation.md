# StrideX Public Spectator Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the first public spectator experience on web so anyone can follow a race and search for an athlete without signing in, while preserving the mobile app as the richer logged-in experience.

**Architecture:** Reuse the existing live-tracking domain and websocket feed, but add a dedicated public read layer in the backend plus new public web routes in `apps/web`. Keep Phase 1 intentionally narrow: public race hub, athlete search, public athlete detail/share route, limited leaderboard/splits, and lightweight upgrade/account-creation prompts. Do not mix premium entitlements, push alerts, or RFID device ingestion into this pass.

**Tech Stack:** Spring Boot 3.2, Spring Security, React 19 + Vite, TypeScript, React Router, STOMP/WebSocket, shared API contract package

---

## Scope Split

This strategy spans multiple independent subsystems. This document covers **Phase 1 only**:
- public spectator web
- public backend read endpoints
- route/security/contract updates
- docs updates needed to support the first public rollout

Create separate follow-on plans after this ships for:
- premium mobile fan/athlete features
- organizer monetization and sponsorship controls
- RFID/timing device ingest and monitoring

## File Map

### Backend

**Create**
- `apps/backend/src/main/java/com/racetracker/controller/PublicTrackingController.java`
- `apps/backend/src/main/java/com/racetracker/service/PublicTrackingService.java`
- `apps/backend/src/main/java/com/racetracker/service/impl/PublicTrackingServiceImpl.java`
- `apps/backend/src/main/java/com/racetracker/dto/publictracking/PublicRaceHubResponse.java`
- `apps/backend/src/main/java/com/racetracker/dto/publictracking/PublicRaceAthleteSummaryResponse.java`
- `apps/backend/src/main/java/com/racetracker/dto/publictracking/PublicAthleteTrackingResponse.java`
- `apps/backend/src/test/java/com/racetracker/controller/PublicTrackingControllerWebMvcTest.java`

**Modify**
- `apps/backend/src/main/java/com/racetracker/config/SecurityConfig.java`
- `apps/backend/src/main/java/com/racetracker/repository/ParticipantRepository.java`
- `apps/backend/src/main/java/com/racetracker/repository/CheckpointRecordRepository.java`
- `apps/backend/src/main/java/com/racetracker/repository/RaceRepository.java` only if a targeted public query is required
- `apps/backend/src/test/java/com/racetracker/controller/ControllerAuthorizationMatrixTest.java`
- `apps/backend/README.md`

### Web

**Create**
- `apps/web/src/pages/PublicRaceHubPage.tsx`
- `apps/web/src/pages/PublicAthleteTrackingPage.tsx`
- `apps/web/src/components/public/PublicRaceHero.tsx`
- `apps/web/src/components/public/PublicAthleteSearch.tsx`
- `apps/web/src/components/public/PublicLeaderboard.tsx`
- `apps/web/src/components/public/PublicAthleteCard.tsx`

**Modify**
- `apps/web/src/App.tsx`
- `apps/web/src/lib/api.ts`
- `apps/web/src/types/index.ts`

### Contracts and docs

**Modify**
- `packages/contracts/contract.json`
- `docs/api-contract.md`
- `README.md`
- `DEVELOPMENT_GUIDE.md`

## Public API Shape

Phase 1 public endpoints should be explicitly read-only and safe for anonymous access:

```text
GET /api/v1/public/races/{raceId}/hub
GET /api/v1/public/races/{raceId}/tracking/live
GET /api/v1/public/races/{raceId}/tracking/athletes/{participantId}
GET /api/v1/public/races/{raceId}/tracking/search?q=<term>
```

Return only spectator-safe fields:
- race id, name, status, date, city, country, distance, banner/logo
- participant id, display name, bib number, public status, last checkpoint, elapsed time, average pace, predicted finish, distance remaining
- split names and times
- public leaderboard ordering

Do **not** return:
- phone, email, medical info, device tokens, emergency contacts, internal notes, admin-only audit data

## Web Route Shape

Add public routes without changing admin route semantics:

```text
/race/:raceId
/race/:raceId/athlete/:participantId
```

Public route behavior:
- no auth required
- mobile-first layout
- search by name or bib
- leaderboard + map + athlete detail in one shareable surface
- clear but soft CTA to sign up / open mobile app for richer features

## Task 1: Add Public Backend Read Layer

**Files:**
- Create: `apps/backend/src/main/java/com/racetracker/controller/PublicTrackingController.java`
- Create: `apps/backend/src/main/java/com/racetracker/service/PublicTrackingService.java`
- Create: `apps/backend/src/main/java/com/racetracker/service/impl/PublicTrackingServiceImpl.java`
- Create: `apps/backend/src/main/java/com/racetracker/dto/publictracking/PublicRaceHubResponse.java`
- Create: `apps/backend/src/main/java/com/racetracker/dto/publictracking/PublicRaceAthleteSummaryResponse.java`
- Create: `apps/backend/src/main/java/com/racetracker/dto/publictracking/PublicAthleteTrackingResponse.java`
- Modify: `apps/backend/src/main/java/com/racetracker/repository/ParticipantRepository.java`
- Modify: `apps/backend/src/main/java/com/racetracker/repository/CheckpointRecordRepository.java`
- Modify: `apps/backend/src/main/java/com/racetracker/repository/RaceRepository.java` if needed

- [ ] **Step 1: Write the failing controller test for anonymous public access**

Create `PublicTrackingControllerWebMvcTest` with at least:

```java
@WebMvcTest(controllers = PublicTrackingController.class)
class PublicTrackingControllerWebMvcTest {
    @Test
    void getHub_allowsAnonymousAccess() throws Exception { ... }

    @Test
    void getAthleteTracking_returnsOnlyPublicFields() throws Exception { ... }
}
```

- [ ] **Step 2: Run the focused backend test and verify it fails**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=PublicTrackingControllerWebMvcTest test
```

Expected: FAIL because `PublicTrackingController` and DTOs do not exist yet.

- [ ] **Step 3: Create the public DTOs with a deliberately narrow field set**

Use compact DTOs such as:

```java
public class PublicRaceHubResponse {
    private UUID raceId;
    private String raceName;
    private String status;
    private String raceDate;
    private String city;
    private String country;
    private BigDecimal distanceKm;
    private List<PublicRaceAthleteSummaryResponse> leaderboard;
}
```

```java
public class PublicAthleteTrackingResponse {
    private UUID participantId;
    private String participantName;
    private String bibNumber;
    private String status;
    private String lastCheckpoint;
    private String elapsedTime;
    private String averagePace;
    private String predictedFinishTime;
    private BigDecimal distanceRemaining;
    private List<CheckpointSplitDto> splits;
}
```

- [ ] **Step 4: Create `PublicTrackingService` and `PublicTrackingServiceImpl`**

Implement methods:

```java
PublicRaceHubResponse getRaceHub(UUID raceId);
List<PublicRaceAthleteSummaryResponse> getLiveAthletes(UUID raceId, String query);
PublicAthleteTrackingResponse getAthleteTracking(UUID raceId, UUID participantId);
```

Implementation rules:
- verify race exists
- verify race is public enough for anonymous viewing
- reuse tracking calculations from existing services where reasonable
- never expose private participant fields
- limit Phase 1 leaderboard to top-N live athletes plus search results

- [ ] **Step 5: Add any minimal repository queries required**

Prefer small additions such as:
- `ParticipantRepository.findByRaceIdAndBibNumber(...)` reuse if enough
- add case-insensitive search by bib/name only if frontend-side filtering becomes too heavy
- add a top-finishers or top-live query only if Phase 1 needs server ordering beyond current data

- [ ] **Step 6: Add `PublicTrackingController`**

Expose:

```java
@RestController
@RequestMapping("/v${racetracker.api.version}/public/races/{raceId}")
public class PublicTrackingController {
    @GetMapping("/hub") ...
    @GetMapping("/tracking/live") ...
    @GetMapping("/tracking/search") ...
    @GetMapping("/tracking/athletes/{participantId}") ...
}
```

Use plain anonymous access. Do not add `@PreAuthorize`.

- [ ] **Step 7: Re-run the focused backend test**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=PublicTrackingControllerWebMvcTest test
```

Expected: PASS

- [ ] **Step 8: Commit the backend public read layer**

```bash
git add \
  apps/backend/src/main/java/com/racetracker/controller/PublicTrackingController.java \
  apps/backend/src/main/java/com/racetracker/service/PublicTrackingService.java \
  apps/backend/src/main/java/com/racetracker/service/impl/PublicTrackingServiceImpl.java \
  apps/backend/src/main/java/com/racetracker/dto/publictracking \
  apps/backend/src/main/java/com/racetracker/repository/ParticipantRepository.java \
  apps/backend/src/main/java/com/racetracker/repository/CheckpointRecordRepository.java \
  apps/backend/src/test/java/com/racetracker/controller/PublicTrackingControllerWebMvcTest.java
git commit -m "feat(api): add public spectator tracking endpoints"
```

## Task 2: Open Public Routes Safely Through Security and Contract Updates

**Files:**
- Modify: `apps/backend/src/main/java/com/racetracker/config/SecurityConfig.java`
- Modify: `apps/backend/src/test/java/com/racetracker/controller/ControllerAuthorizationMatrixTest.java`
- Modify: `packages/contracts/contract.json`
- Modify: `docs/api-contract.md`

- [ ] **Step 1: Write the failing security expectation**

Add assertions that:
- public tracking routes are anonymous in the security config
- existing admin/athlete routes remain protected

For the reflection matrix test, add a dedicated assertion block or a separate focused test if that is cleaner than forcing `@PreAuthorize` expectations onto anonymous endpoints.

- [ ] **Step 2: Run the focused security tests and verify failure**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=ControllerAuthorizationMatrixTest,PublicTrackingControllerWebMvcTest test
```

Expected: FAIL until `SecurityConfig` is updated.

- [ ] **Step 3: Permit the new public route prefix in `SecurityConfig`**

Add:

```java
apiPrefix + "/public/races/**"
```

to the anonymous allowlist, while keeping:
- auth endpoints public
- admin and user endpoints authenticated
- websocket behavior unchanged for now

- [ ] **Step 4: Extend the shared contract**

Add endpoints such as:

```json
"publicRaceHub": "/public/races/:id/hub",
"publicRaceLive": "/public/races/:id/tracking/live",
"publicAthleteTracking": "/public/races/:id/tracking/athletes/:participantId"
```

Do not change the versioning model in this task.

- [ ] **Step 5: Update contract documentation**

In `docs/api-contract.md`, document:
- which consumers use the new public endpoints
- that they are anonymous
- that they must remain limited to spectator-safe fields

- [ ] **Step 6: Re-run focused backend tests and contract validation**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=ControllerAuthorizationMatrixTest,PublicTrackingControllerWebMvcTest test
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm contracts:validate
```

Expected:
- Maven tests PASS
- contract validation PASS

- [ ] **Step 7: Commit the security and contract work**

```bash
git add \
  apps/backend/src/main/java/com/racetracker/config/SecurityConfig.java \
  apps/backend/src/test/java/com/racetracker/controller/ControllerAuthorizationMatrixTest.java \
  packages/contracts/contract.json \
  docs/api-contract.md
git commit -m "feat(platform): expose public spectator route contract"
```

## Task 3: Build the Public Race Hub and Athlete Tracking Pages in Web

**Files:**
- Create: `apps/web/src/pages/PublicRaceHubPage.tsx`
- Create: `apps/web/src/pages/PublicAthleteTrackingPage.tsx`
- Create: `apps/web/src/components/public/PublicRaceHero.tsx`
- Create: `apps/web/src/components/public/PublicAthleteSearch.tsx`
- Create: `apps/web/src/components/public/PublicLeaderboard.tsx`
- Create: `apps/web/src/components/public/PublicAthleteCard.tsx`
- Modify: `apps/web/src/App.tsx`
- Modify: `apps/web/src/lib/api.ts`
- Modify: `apps/web/src/types/index.ts`

- [ ] **Step 1: Write the failing web build expectation**

Create the new route imports/usages in `App.tsx` first, before the new page files exist, so the build fails for the right reason.

- [ ] **Step 2: Run the web build and verify it fails**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter web build
```

Expected: FAIL because the public pages and public API client methods do not exist yet.

- [ ] **Step 3: Add public API types**

In `apps/web/src/types/index.ts`, add narrow public-facing types:

```ts
export interface PublicRaceAthleteSummary { ... }
export interface PublicRaceHub { ... }
export interface PublicAthleteTracking { ... }
```

Keep them separate from the admin live-tracking types to avoid accidentally widening admin expectations.

- [ ] **Step 4: Add public API client methods**

In `apps/web/src/lib/api.ts`, add:

```ts
publicTrackingApi.getRaceHub(raceId)
publicTrackingApi.getLiveAthletes(raceId, query?)
publicTrackingApi.getAthleteTracking(raceId, participantId)
```

Rules:
- no auth token required for these methods
- reuse `fetchApi` only if it can tolerate anonymous requests cleanly
- otherwise add a tiny anonymous `fetchPublicApi` helper

- [ ] **Step 5: Add the public route entries**

In `apps/web/src/App.tsx`, add:

```tsx
<Route path="/race/:raceId" element={<PublicRaceHubPage />} />
<Route path="/race/:raceId/athlete/:participantId" element={<PublicAthleteTrackingPage />} />
```

Do not move or weaken the existing admin route tree.

- [ ] **Step 6: Build `PublicRaceHubPage`**

The page should:
- fetch public race hub data
- fetch live athlete data
- offer search by bib/name
- render race header, public leaderboard, and mobile-friendly athlete list
- deep-link to `/race/:raceId/athlete/:participantId`
- include a restrained CTA such as “Create an account for alerts and favorites”

Prefer reusing visual language from:
- `apps/web/src/components/dashboard/RunnerCard.tsx`
- `apps/web/src/components/dashboard/AeroPanel.tsx`
- `apps/web/src/components/dashboard/AnalyticsTray.tsx`

without importing admin-only layout assumptions.

- [ ] **Step 7: Build `PublicAthleteTrackingPage`**

The page should:
- fetch one athlete’s public tracking payload
- show bib, athlete name, status, latest checkpoint, elapsed time, average pace, predicted finish, limited split timeline
- provide a copy/share link affordance
- provide a soft upgrade CTA for saved follows and alerts

- [ ] **Step 8: Re-run the web build**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter web build
```

Expected: PASS

- [ ] **Step 9: Commit the public web surface**

```bash
git add \
  apps/web/src/App.tsx \
  apps/web/src/lib/api.ts \
  apps/web/src/types/index.ts \
  apps/web/src/pages/PublicRaceHubPage.tsx \
  apps/web/src/pages/PublicAthleteTrackingPage.tsx \
  apps/web/src/components/public
git commit -m "feat(web): add public spectator race pages"
```

## Task 4: Add Upgrade Hooks Without Building Premium Entitlements Yet

**Files:**
- Modify: `apps/web/src/pages/PublicRaceHubPage.tsx`
- Modify: `apps/web/src/pages/PublicAthleteTrackingPage.tsx`
- Modify: `apps/mobile/README.md` only if the deep-link/open-in-app behavior is documented there in this pass

- [ ] **Step 1: Add a failing content check via build review**

Before implementing CTAs, note the required copy/placement in the PR or local checklist:
- one CTA on the race hub
- one CTA on the athlete page
- no blocking modal
- no interruption of live data use

- [ ] **Step 2: Add soft conversion surfaces**

Examples:
- “Sign in to save favorites and get alerts”
- “Open in app for richer race-day tools”

Allowed placements:
- under athlete detail
- below leaderboard
- in page footer

Disallowed placements:
- blocking modal on first load
- forced auth before search

- [ ] **Step 3: Add a free-tier guardrail in copy only**

Copy should make it clear that:
- public web is free and useful
- accounts unlock favorites and alerts
- mobile app is the richer experience

Do not implement paywalls or entitlement logic in this task.

- [ ] **Step 4: Re-run the web build**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter web build
```

Expected: PASS

- [ ] **Step 5: Commit the acquisition hooks**

```bash
git add apps/web/src/pages/PublicRaceHubPage.tsx apps/web/src/pages/PublicAthleteTrackingPage.tsx
git commit -m "feat(web): add public-to-account upgrade hooks"
```

## Task 5: Update Product and Developer Documentation

**Files:**
- Modify: `README.md`
- Modify: `DEVELOPMENT_GUIDE.md`
- Modify: `apps/backend/README.md`

- [ ] **Step 1: Write the failing docs checklist**

Create a short checklist and verify the docs currently miss:
- public spectator web surface
- public route examples
- product positioning between public web and mobile
- explicit note that RFID device ingest is still out of scope for Phase 1

- [ ] **Step 2: Update `README.md`**

Add:
- product surfaces section
- public route examples
- note that web now serves both public spectators and admin workflows

- [ ] **Step 3: Update `DEVELOPMENT_GUIDE.md`**

Add:
- public tracking flow
- backend-to-web public data path
- note that premium mobile and RFID device ingest are later phases

- [ ] **Step 4: Update `apps/backend/README.md`**

Document:
- new public endpoints
- anonymous access expectation
- spectator-safe payload rules

- [ ] **Step 5: Run final validation**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=PublicTrackingControllerWebMvcTest,ControllerAuthorizationMatrixTest,TrackingControllerWebMvcTest test
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm contracts:validate
pnpm --filter web build
```

Expected:
- focused backend tests PASS
- contract validation PASS
- web build PASS

- [ ] **Step 6: Commit the documentation update**

```bash
git add README.md DEVELOPMENT_GUIDE.md apps/backend/README.md
git commit -m "docs: document public spectator foundation"
```

## Execution Notes

- Keep all public DTOs separate from admin DTOs.
- Do not expose authenticated-only tracking endpoints publicly by accident; create dedicated public endpoints.
- Do not add premium entitlements, billing, sponsorship CMS, or RFID device ingest in this plan.
- Reuse existing dashboard visual components where it shortens implementation, but do not leak admin controls into public pages.
- If websocket anonymous subscriptions work cleanly with the current `/ws-tracking` setup, use them. If they do not, fall back to public polling in Phase 1 and defer anonymous live websocket refinement to a later plan.

## Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-31-stridex-public-spectator-foundation.md`.

Recommended next plan after this one ships:
- `2026-05-31-stridex-premium-mobile-fan-features.md`
- `2026-05-31-stridex-rfid-timing-ingest-and-ops.md`
