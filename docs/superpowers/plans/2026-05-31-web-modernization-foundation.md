# StrideX Web Modernization Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modernize `apps/web` from a plain `BrowserRouter` SPA shell toward a React Router framework-style route architecture that better supports public spectator growth without rewriting the admin/operator surface.

**Architecture:** Introduce React Router's Vite integration and route-module structure while preserving the existing page/component investment. Migrate the current route tree into framework-managed route modules first, then move the public spectator routes to typed `clientLoader`-based data loading and revalidation. Keep the operator/admin routes functional during the migration and defer deeper SSR/prerender work until a later plan.

**Tech Stack:** React 19, React Router v7 framework tooling, Vite, TypeScript, SWR, Tailwind CSS, existing StrideX web components

---

## Scope Guardrail

This plan is intentionally limited to the web modernization foundation only.

Do in this plan:
- framework-style route architecture for `apps/web`
- public spectator route modernization
- test/build tooling needed for this migration
- docs updates specific to the web routing/runtime change

Do **not** do in this plan:
- Expo/mobile modernization
- Spring Boot upgrade
- OpenTelemetry rollout
- PostGIS adoption
- RFID ingest architecture
- public prerendering for dynamic race pages

Follow-on plans should be created separately for:
- mobile runtime modernization
- backend platform/observability modernization
- geospatial and telemetry data modernization
- RFID/bib ingest modernization

## File Map

### Modify

- `apps/web/package.json`
- `apps/web/vite.config.ts`
- `apps/web/tsconfig.json`
- `apps/web/src/main.tsx`
- `apps/web/src/App.tsx`
- `apps/web/src/lib/api.ts`
- `apps/web/src/pages/PublicRaceHubPage.tsx`
- `apps/web/src/pages/PublicAthleteTrackingPage.tsx`
- `README.md`
- `DEVELOPMENT_GUIDE.md`

### Create

- `apps/web/react-router.config.ts`
- `apps/web/vitest.config.ts`
- `apps/web/app/root.tsx`
- `apps/web/app/routes.ts`
- `apps/web/app/routes/home.tsx`
- `apps/web/app/routes/login.tsx`
- `apps/web/app/routes/register.tsx`
- `apps/web/app/routes/unauthorized.tsx`
- `apps/web/app/routes/public-race.tsx`
- `apps/web/app/routes/public-athlete.tsx`
- `apps/web/app/routes/admin-layout.tsx`
- `apps/web/app/routes/admin-dashboard.tsx`
- `apps/web/app/routes/admin-races.tsx`
- `apps/web/app/routes/admin-race-detail.tsx`
- `apps/web/app/routes/admin-race-participants.tsx`
- `apps/web/app/routes/admin-race-live.tsx`
- `apps/web/app/routes/admin-race-checkpoints.tsx`
- `apps/web/app/routes/admin-race-results.tsx`
- `apps/web/app/routes/admin-settings.tsx`
- `apps/web/app/routes/app-layout.tsx`
- `apps/web/app/routes/app-dashboard.tsx`
- `apps/web/app/routes/app-stats.tsx`
- `apps/web/app/routes/fallback.tsx`
- `apps/web/src/test/setup.ts`
- `apps/web/src/test/router.smoke.test.tsx`
- `apps/web/src/test/public-routes-loader.test.tsx`

### Optional creates if extraction improves clarity

- `apps/web/src/router/publicLoaders.ts`
- `apps/web/src/router/getSafePublicReturnPath.ts`

## Intended Structure

Use React Router route modules in `apps/web/app/` as the orchestration layer and keep the existing UI in `apps/web/src/`.

- `app/root.tsx` owns global providers and document/app shell wiring
- `app/routes.ts` owns the route tree
- each route module should mostly import and render an existing page/layout component from `src/`
- public route modules should own `clientLoader` functions and pass loader data into the page layer
- `src/` remains the home for reusable components, contexts, API clients, and page presentation

This keeps the migration incremental instead of forcing a large file move.

## Task 1: Add Framework Tooling and Test Harness

**Files:**
- Modify: `apps/web/package.json`
- Modify: `apps/web/vite.config.ts`
- Modify: `apps/web/tsconfig.json`
- Create: `apps/web/react-router.config.ts`
- Create: `apps/web/vitest.config.ts`
- Create: `apps/web/src/test/setup.ts`

- [ ] **Step 1: Write down the baseline runtime/build behavior**

Record in the working notes:
- current entry uses `BrowserRouter` in `apps/web/src/main.tsx`
- current app routes live in `apps/web/src/App.tsx`
- there is no web test harness today

- [ ] **Step 2: Run the current web build as a baseline**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter web build
```

Expected: PASS

- [ ] **Step 3: Add the framework/tooling dependencies**

Update `apps/web/package.json` to add the minimal modern routing/test tooling:
- `react-router` / `react-router-dom` aligned to the same modern major
- `@react-router/dev`
- `vitest`
- `jsdom`
- `@testing-library/react`
- `@testing-library/jest-dom`

Also add scripts for:

```json
{
  "dev": "react-router dev",
  "build": "react-router build",
  "typecheck": "react-router typegen && tsc -b",
  "test": "vitest run"
}
```

- [ ] **Step 4: Configure Vite and React Router**

Update `apps/web/vite.config.ts` and create `apps/web/react-router.config.ts` so the web app can run in React Router's Vite-integrated mode while preserving the existing `/api` proxy.

Target behaviors:
- route modules live under `apps/web/app`
- existing `@` alias still resolves to `apps/web/src`
- dev proxy for `/api` still targets `http://127.0.0.1:8081`

- [ ] **Step 5: Add the test harness**

Create `apps/web/vitest.config.ts` and `apps/web/src/test/setup.ts` with:
- `jsdom` environment
- Testing Library setup
- `@testing-library/jest-dom` registration

- [ ] **Step 6: Run install and type generation**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm install
pnpm --filter web exec react-router typegen
```

Expected: PASS

- [ ] **Step 7: Commit the tooling foundation**

```bash
git add apps/web/package.json apps/web/vite.config.ts apps/web/tsconfig.json apps/web/react-router.config.ts apps/web/vitest.config.ts apps/web/src/test/setup.ts pnpm-lock.yaml
git commit -m "chore(web): add route-module tooling foundation"
```

## Task 2: Migrate the Existing Route Tree to Route Modules

**Files:**
- Modify: `apps/web/src/main.tsx`
- Modify: `apps/web/src/App.tsx`
- Create: `apps/web/app/root.tsx`
- Create: `apps/web/app/routes.ts`
- Create: `apps/web/app/routes/home.tsx`
- Create: `apps/web/app/routes/login.tsx`
- Create: `apps/web/app/routes/register.tsx`
- Create: `apps/web/app/routes/unauthorized.tsx`
- Create: `apps/web/app/routes/admin-layout.tsx`
- Create: `apps/web/app/routes/admin-dashboard.tsx`
- Create: `apps/web/app/routes/admin-races.tsx`
- Create: `apps/web/app/routes/admin-race-detail.tsx`
- Create: `apps/web/app/routes/admin-race-participants.tsx`
- Create: `apps/web/app/routes/admin-race-live.tsx`
- Create: `apps/web/app/routes/admin-race-checkpoints.tsx`
- Create: `apps/web/app/routes/admin-race-results.tsx`
- Create: `apps/web/app/routes/admin-settings.tsx`
- Create: `apps/web/app/routes/app-layout.tsx`
- Create: `apps/web/app/routes/app-dashboard.tsx`
- Create: `apps/web/app/routes/app-stats.tsx`
- Create: `apps/web/app/routes/fallback.tsx`

- [ ] **Step 1: Write a route smoke test before changing the shell**

Create `apps/web/src/test/router.smoke.test.tsx` with assertions that the route tree can resolve at least:
- `/race/demo`
- `/race/demo/athlete/demo-athlete`
- `/login`
- `/admin`
- `/app`

Use a memory-based router/render test rather than a browser-level test.

- [ ] **Step 2: Run the smoke test and confirm it fails**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter web test -- router.smoke.test.tsx
```

Expected: FAIL because the route-module structure does not exist yet

- [ ] **Step 3: Create `app/root.tsx` and move global providers there**

`app/root.tsx` should own:
- `ThemeProvider`
- `AuthProvider`
- `Toaster`
- route `Outlet`

Do not keep `BrowserRouter` in `src/main.tsx`.

- [ ] **Step 4: Create route modules that wrap the existing pages**

Create a route module per current route and import the existing page/layout components from `src/`.

Important rule:
- this step should preserve behavior, not redesign pages
- use route-module wrappers as adapters around the existing `src/pages/*` and `src/components/*`

- [ ] **Step 5: Shrink `src/main.tsx` and `src/App.tsx`**

Target outcome:
- `src/main.tsx` only boots the framework runtime
- `src/App.tsx` either disappears from active routing responsibility or becomes a small compatibility wrapper if strictly needed

Avoid leaving two competing top-level routers in place.

- [ ] **Step 6: Run build, typecheck, and route smoke test**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter web exec react-router typegen
pnpm --filter web test -- router.smoke.test.tsx
pnpm --filter web build
```

Expected: PASS

- [ ] **Step 7: Commit the route-module shell migration**

```bash
git add apps/web/src/main.tsx apps/web/src/App.tsx apps/web/app apps/web/src/test/router.smoke.test.tsx
git commit -m "refactor(web): migrate route tree to route modules"
```

## Task 3: Move Public Spectator Routes to Loader-Based Data Flow

**Files:**
- Modify: `apps/web/src/lib/api.ts`
- Modify: `apps/web/src/pages/PublicRaceHubPage.tsx`
- Modify: `apps/web/src/pages/PublicAthleteTrackingPage.tsx`
- Create: `apps/web/app/routes/public-race.tsx`
- Create: `apps/web/app/routes/public-athlete.tsx`
- Create: `apps/web/src/test/public-routes-loader.test.tsx`
- Optional create: `apps/web/src/router/publicLoaders.ts`

- [ ] **Step 1: Write failing loader tests**

Create `apps/web/src/test/public-routes-loader.test.tsx` to cover:
- race hub loader fetches public hub + live athletes
- athlete loader fetches the selected athlete tracking payload
- loader surfaces a typed error path when required params are missing

- [ ] **Step 2: Run the loader tests and confirm failure**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter web test -- public-routes-loader.test.tsx
```

Expected: FAIL because public route modules do not expose loaders yet

- [ ] **Step 3: Extract public data fetch helpers if needed**

If `apps/web/src/lib/api.ts` becomes noisy, create `apps/web/src/router/publicLoaders.ts` for:
- `loadPublicRaceHub(raceId)`
- `loadPublicAthleteTracking(raceId, participantId)`

Keep `fetchPublicApi` and `publicTrackingApi` as the shared transport boundary.

- [ ] **Step 4: Add `clientLoader` to the public route modules**

`apps/web/app/routes/public-race.tsx` should:
- read `raceId`
- load race hub data and live athlete data
- pass the initial data into `PublicRaceHubPage`

`apps/web/app/routes/public-athlete.tsx` should:
- read `raceId` + `participantId`
- load athlete tracking data
- pass the initial data into `PublicAthleteTrackingPage`

Use route-module loader data for the first render rather than forcing the page components to cold-start from `useEffect`.

- [ ] **Step 5: Refactor the page components to accept initial data**

`PublicRaceHubPage.tsx` and `PublicAthleteTrackingPage.tsx` should:
- accept initial loader data via props or `useLoaderData`-friendly wrappers
- keep manual refresh/search logic where still useful
- avoid duplicate first-render fetches once loader data exists

- [ ] **Step 6: Re-run tests and build**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter web exec react-router typegen
pnpm --filter web test -- public-routes-loader.test.tsx
pnpm --filter web build
```

Expected: PASS

- [ ] **Step 7: Commit the public route data modernization**

```bash
git add apps/web/src/lib/api.ts apps/web/src/pages/PublicRaceHubPage.tsx apps/web/src/pages/PublicAthleteTrackingPage.tsx apps/web/app/routes/public-race.tsx apps/web/app/routes/public-athlete.tsx apps/web/src/test/public-routes-loader.test.tsx apps/web/src/router/publicLoaders.ts
git commit -m "feat(web): modernize public route data loading"
```

## Task 4: Document the New Web Routing Foundation

**Files:**
- Modify: `README.md`
- Modify: `DEVELOPMENT_GUIDE.md`

- [ ] **Step 1: Note the doc delta before editing**

The docs should explain:
- the web app now uses route modules
- public spectator routes are the first routes modernized under the new model
- `apps/web` remains Vite-based
- deeper prerender/SSR work is deferred

- [ ] **Step 2: Update `README.md`**

Add concise notes covering:
- route-module architecture
- public spectator web as the first modernization target
- current runtime commands for the web app

- [ ] **Step 3: Update `DEVELOPMENT_GUIDE.md`**

Add concise notes covering:
- why the web app is moving toward route-managed data loading
- where route modules live
- what remains deferred to later modernization plans

- [ ] **Step 4: Run final validation**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter web exec react-router typegen
pnpm --filter web test
pnpm --filter web build
```

Expected:
- type generation PASS
- route tests PASS
- web build PASS

- [ ] **Step 5: Commit the docs update**

```bash
git add README.md DEVELOPMENT_GUIDE.md
git commit -m "docs(web): document route-module foundation"
```

## Execution Notes

- Keep existing UI components in `apps/web/src/`; do not perform a large component relocation during this plan.
- Prefer route-module wrappers that import existing pages rather than rewriting entire screens.
- Treat the public spectator routes as the proving ground for the new data-loading model.
- Do not attempt dynamic race-page prerendering in this plan.
- If React Router framework-mode adoption proves too invasive for the entire route tree, preserve the plan goal by keeping the route-module shell and deferring deeper admin-route modernization rather than backing out completely.

## Follow-on Plans

After this plan ships, create:
- `2026-05-31-mobile-runtime-modernization-foundation.md`
- `2026-05-31-backend-platform-observability-foundation.md`
- `2026-05-31-geospatial-telemetry-modernization.md`
- `2026-05-31-rfid-bib-ingest-modernization.md`
