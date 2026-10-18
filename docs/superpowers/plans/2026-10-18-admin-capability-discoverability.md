# Admin capability discoverability implementation plan

> **For agentic workers:** Execute this plan task by task with an independent review
> checkpoint after implementation. Do not commit until explicitly approved.

**Goal:** Make authorized admin and race-operations capabilities discoverable and stable
when the database is empty, loading, filtered, or temporarily unavailable, without exposing
privileged operations to roles that cannot perform them.

**Architecture:** Preserve the React Router 7 route tree, `DashboardLayout` shell, existing
`AuthContext` role predicates, and backend authorization. Move the shared race-navigation
catalog into a small web configuration module. `RacesPage` consumes that catalog for a
role-aware capability preview. `DashboardPage` and `RacesPage` keep remote state explicit
with loading, error, retry, and empty branches. The personal shell is explicitly limited to
athletes and spectators, with role-specific navigation and dashboard cards. No API or
database changes are required.

**Tech stack:** React, TypeScript strict mode, React Router 7, react-i18next, existing shadcn
UI primitives, Vitest/Testing Library, pnpm.

## Task 1: Shared capability catalog

**Files:**

- Add `apps/web/src/config/adminNavigation.ts`.
- Update `apps/web/src/components/layout/DashboardLayout.tsx`.
- Update `apps/web/src/pages/RacesPage.tsx`.

**Work:**

- Define the race operation item type, icon references, translation keys, and role lists in
  one module.
- Export a helper that returns only capabilities allowed for a `UserRole`.
- Preserve current paths and role assignments exactly.
- Keep the sidebar as the live navigation consumer and use the same catalog for the empty
  state, avoiding a second hand-maintained list.

**Verification:** TypeScript compilation and existing role-navigation tests.

## Task 2: Stable race-management states

**Files:**

- `apps/web/src/pages/RacesPage.tsx`.
- `apps/web/src/i18n/locales/en/nav.json`.

**Work:**

- Track a user-visible loading error instead of reporting only a toast.
- Render skeleton cards while the list is loading.
- Render an inline retryable error state when loading fails.
- Render a structured empty state when no races match, including the role-aware capability
  preview and the administrator create-race action.
- Preserve search, status, and archive behavior and do not create local seed data.
- Add translation keys for the new state copy; use existing nav labels for capabilities.

**Verification:** Add focused tests for loading, failure/retry, empty administrator, and
empty operations-role states.

## Task 3: Stable dashboard states

**Files:**

- `apps/web/src/pages/DashboardPage.tsx`.
- `apps/web/src/i18n/locales/en/nav.json`.

**Work:**

- Keep dashboard statistics and recent races resilient when one request fails.
- Show a retryable inline error state and an actionable no-races state instead of an
  unexplained paragraph.
- Preserve the existing dashboard route and admin role guard.

**Verification:** Extend router smoke coverage for the empty dashboard and failed request.

## Task 4: Independent implementation review

**Review checklist:**

- No operation is newly visible to a role outside its existing backend authorization.
- No client literal API paths or route changes were introduced.
- Loading/error/empty branches are mutually exclusive and do not flash stale content.
- Existing navigation labels, route paths, and public spectator boundaries remain intact.
- i18n check does not report missing default-locale keys.

## Task 5: Personal-role surfaces and direct-route enforcement

**Files:**

- `apps/web/src/contexts/AuthContext.tsx`.
- `apps/web/app/routes/app-layout.tsx` and `apps/web/app/routes/app-settings.tsx`.
- `apps/web/src/components/layout/UserLayout.tsx`.
- `apps/web/src/pages/UserDashboardPage.tsx` and `apps/web/src/pages/UserStatsPage.tsx`.
- Race-scoped route modules under `apps/web/app/routes/admin-race-*.tsx`.

**Work:**

- Keep `SUPER_ADMIN`, `RACE_ADMIN`, `OFFICIAL`, and `VOLUNTEER` in the operations shell;
  keep `ATHLETE` and `SPECTATOR` in the personal shell; route unknown roles to a stable
  unauthorized page.
- Give athletes links for registrations, official results, performance, notifications, and
  account settings.
- Give spectators links for followed athletes, public tracking, notifications, and account
  settings without calling athlete-only registration or personal-results endpoints.
- Apply the same role matrix at each race-scoped route boundary so hiding a sidebar item
  is not the only protection against direct URL access.
- Preserve public anonymous race pages as the spectator entry point for unauthenticated users.

**Verification:** Add role-helper tests, athlete/spectator route smoke coverage, operations
staff rejection from `/app`, and direct-route authorization coverage. Run i18n, lint,
typecheck, tests, and build.

## Task 6: Release verification

Run, as available:

```bash
pnpm contracts:validate
pnpm i18n:check
pnpm --filter web lint
pnpm --filter web typecheck
pnpm --filter web test
pnpm --filter web build
```

Then manually verify the running local stack at `/admin`, `/admin/races`, and a real
`/admin/races/:raceId` route with the existing test accounts. Report any backend-only
admin capabilities that remain outside the web shell as explicit follow-up gaps rather than
claiming the portal exposes every backend endpoint.
