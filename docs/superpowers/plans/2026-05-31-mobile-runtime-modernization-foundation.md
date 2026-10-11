# StrideX Mobile Runtime Modernization Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade `apps/mobile` to an Expo SDK 54 bridge baseline and harden the local-first tracking runtime so race sessions and queued GPS points survive normal mobile lifecycle interruptions.

**Architecture:** Modernize in place. Keep Expo Router, Zustand, Expo Location, and SQLite, but align the runtime package graph, fix TypeScript/API drift, persist active tracking sessions in local storage/SQLite, add safer queued sync semantics, and scaffold development-build/EAS Update readiness without requiring a production Expo project setup in this pass.

**Tech Stack:** Expo SDK 54, React Native 0.81, React 19.1, Expo Router, Expo Location, Expo Task Manager, Expo BackgroundTask, Expo SQLite, Expo Updates, Expo Notifications, Zustand, TypeScript, pnpm

---

## Scope Guardrail

This plan is intentionally limited to the mobile runtime and offline tracking foundation.

Do in this plan:

- Expo SDK 54 package alignment for `apps/mobile`
- Node/tooling baseline alignment required by SDK 54
- TypeScript/runtime API drift fixes
- persistent active tracking session recovery
- safer SQLite queued-location sync behavior
- deferrable background sync foundation
- development-build, OTA update, and notification readiness docs/config
- mobile README and root docs updates tied to this runtime modernization

Do **not** do in this plan:

- Expo SDK 55 migration
- mandatory New Architecture-only migration
- mobile UI redesign
- premium mobile fan features
- backend push-token registration
- backend tracking contract changes unless an existing mobile endpoint is broken
- PostGIS/geospatial route-matching work
- RFID/bib ingest work
- store submission or EAS project creation with real account identifiers

Follow-on plans should remain separate for:

- Expo SDK 55 / New Architecture migration
- premium mobile notifications and fan features
- mobile observability/crash reporting
- geospatial and telemetry modernization
- RFID/bib ingest modernization

## File Map

### Modify

- `package.json`
- `pnpm-lock.yaml`
- `apps/mobile/package.json`
- `apps/mobile/app.json`
- `apps/mobile/README.md`
- `apps/mobile/types/index.ts`
- `apps/mobile/lib/db.ts`
- `apps/mobile/lib/tracking-service.ts`
- `apps/mobile/store/tracking.ts`
- `apps/mobile/app/_layout.tsx`
- `apps/mobile/app/tracking.tsx`
- `README.md`
- `DEVELOPMENT_GUIDE.md`

### Create

- `.node-version`
- `apps/mobile/eas.json`
- `apps/mobile/lib/mobile-runtime.ts`
- `apps/mobile/lib/notifications.ts`

### Optional create if needed for testability

- `apps/mobile/lib/tracking-session.ts`
- `apps/mobile/lib/location-queue.ts`
- `apps/mobile/lib/__tests__/tracking-session.test.ts`
- `apps/mobile/lib/__tests__/location-queue.test.ts`

Only create the optional files if the implementation becomes hard to test or reason about inside the current `tracking-service.ts`. Prefer focused extraction over growing `tracking-service.ts` further.

## Intended Outcome

After this plan:

- `apps/mobile` has a coherent Expo SDK 54 runtime baseline.
- root Node expectations match Expo SDK 54's documented Node requirement.
- TypeScript and lint pass for the mobile workspace.
- foreground and background tracking recover the active race from persisted local state.
- failed sync attempts keep GPS points queued for retry.
- deferrable background sync exists as a catch-up mechanism, not as the primary live tracking mechanism.
- update and notification readiness are documented and configured without fake production identifiers.

## Task 1: Record The Baseline And Align Runtime Tooling

**Files:**

- Modify: `package.json`
- Modify: `apps/mobile/package.json`
- Modify: `pnpm-lock.yaml`
- Create: `.node-version`

- [ ] **Step 1: Record the current mobile baseline**

Write down in working notes:

- current Expo version from `apps/mobile/package.json`
- current React Native and React versions
- current TypeScript version
- current `expo-sqlite`, `expo-task-manager`, and `eslint-config-expo` versions
- current root Node engine from `package.json`

- [ ] **Step 2: Run current validation and confirm the known failure**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter race-tracker-mobile exec tsc --noEmit
```

Expected: FAIL with the known SDK/type drift, including:

- `rehydrateFromLocalDb` missing from `TrackingState`
- SQLite generic calls on an `any` database handle
- `pausesLocationUpdatesAutomatically` not accepted by `LocationTaskOptions`
- TypeScript module option mismatch from the current SDK/tooling graph

- [ ] **Step 3: Raise the Node baseline for SDK 54**

Update root `package.json`:

```json
{
  "engines": {
    "node": ">=20.19"
  }
}
```

Create `.node-version`:

```text
20.19.0
```

Rationale: Expo SDK 54's documented minimum Node version is 20.19.x.

- [ ] **Step 4: Upgrade the mobile runtime to Expo SDK 54**

Use Expo's SDK-aware installer rather than hand-aligning every package.

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter race-tracker-mobile add expo@~54.0.0 react@19.1.0 react-dom@19.1.0
pnpm --filter race-tracker-mobile exec expo install --fix
```

Expected:

- `apps/mobile/package.json` moves to Expo SDK 54-compatible versions
- `react-native` moves to an SDK 54-compatible `0.81.x`
- Expo packages align with the SDK 54 bundled versions
- `pnpm-lock.yaml` updates

If `expo install --fix` cannot resolve a package:

- prefer Expo's recommended bundled version
- remove or replace only packages that block SDK 54 compatibility
- do not add broad UI/library changes

- [ ] **Step 5: Add runtime foundation packages**

Install only the packages needed for this foundation:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter race-tracker-mobile exec expo install expo-background-task expo-updates expo-notifications
pnpm --filter race-tracker-mobile add -D expo-doctor
```

Do not add backend push-token libraries or analytics/crash-reporting packages in this task.

- [ ] **Step 6: Run Expo Doctor**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter race-tracker-mobile exec expo-doctor
```

Expected: PASS or only warnings that are explicitly documented in the working notes.

- [ ] **Step 7: Commit the runtime baseline**

```bash
git add package.json .node-version apps/mobile/package.json pnpm-lock.yaml
git commit -m "chore(mobile): align Expo SDK 54 runtime"
```

If `apps/mobile/app.json` or other config files were changed by Expo tooling in this task, include only those directly related changes in this commit.

## Task 2: Fix Mobile TypeScript And Expo API Drift

**Files:**

- Modify: `apps/mobile/types/index.ts`
- Modify: `apps/mobile/lib/db.ts`
- Modify: `apps/mobile/lib/tracking-service.ts`
- Modify: `apps/mobile/store/tracking.ts`
- Modify: `apps/mobile/app/tracking.tsx`

- [ ] **Step 1: Fix the tracking store public type**

Update `TrackingState` in `apps/mobile/types/index.ts` so it matches the implemented store.

Required shape:

```ts
export interface TrackingState {
  isTracking: boolean;
  currentRace: Race | null;
  locations: GpsLocation[];
  checkpointRecords: CheckpointRecord[];
  liveStats: LiveStats | null;
  batterySaver: boolean;
  startTracking: (race: Race) => Promise<void>;
  stopTracking: () => void;
  addLocation: (location: GpsLocation) => void;
  recordCheckpoint: (record: CheckpointRecord) => void;
  setBatterySaver: (enabled: boolean) => void;
  rehydrateFromLocalDb: (raceId: string) => Promise<void>;
}
```

- [ ] **Step 2: Type the SQLite database wrapper**

Update `apps/mobile/lib/db.ts` to export a typed database opener.

Target pattern:

```ts
import * as SQLite from 'expo-sqlite';

export type TrackingDatabase = SQLite.SQLiteDatabase;

export const openDatabaseAsync = SQLite.openDatabaseAsync;
```

Then update `tracking-service.ts` and `store/tracking.ts` so `db` is not `any`.

- [ ] **Step 3: Add row types for tracking queries**

Add local types near the DB code:

```ts
type LocationRow = {
  id: number;
  raceId: string;
  latitude: number;
  longitude: number;
  altitude: number | null;
  accuracy: number | null;
  speed: number | null;
  heading: number | null;
  timestamp: string;
  synced: number;
};
```

Use `database.getAllAsync<LocationRow>(...)` only after the database handle has a typed Expo SQLite type.

- [ ] **Step 4: Fix the Expo Location task option name**

Update `startBackgroundTracking` in `apps/mobile/lib/tracking-service.ts`.

Replace the invalid option:

```ts
pausesLocationUpdatesAutomatically: false
```

with the SDK-supported option:

```ts
pausesUpdatesAutomatically: false
```

If SDK 54's generated types differ after the upgrade, follow SDK 54's type definition exactly.

- [ ] **Step 5: Run TypeScript and confirm only deeper runtime issues remain**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter race-tracker-mobile exec tsc --noEmit
```

Expected: PASS after this task.

If it fails because SDK 54 changed package-specific types:

- fix only mobile type/API compatibility issues
- do not redesign UI screens

- [ ] **Step 6: Commit the type/API fixes**

```bash
git add apps/mobile/types/index.ts apps/mobile/lib/db.ts apps/mobile/lib/tracking-service.ts apps/mobile/store/tracking.ts apps/mobile/app/tracking.tsx
git commit -m "fix(mobile): resolve tracking runtime type drift"
```

## Task 3: Persist And Recover The Active Tracking Session

**Files:**

- Modify: `apps/mobile/lib/tracking-service.ts`
- Modify: `apps/mobile/store/tracking.ts`
- Modify: `apps/mobile/app/_layout.tsx`
- Modify: `apps/mobile/app/tracking.tsx`
- Optional create: `apps/mobile/lib/tracking-session.ts`

- [ ] **Step 1: Extend local database initialization**

Update `initDb` to create a tracking session table:

```sql
CREATE TABLE IF NOT EXISTS tracking_sessions (
  raceId TEXT PRIMARY KEY,
  startedAt TEXT NOT NULL,
  batterySaver INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'active',
  updatedAt TEXT NOT NULL
);
```

Create an index only if needed for the implemented queries:

```sql
CREATE INDEX IF NOT EXISTS idx_tracking_sessions_status_updated
ON tracking_sessions(status, updatedAt);
```

- [ ] **Step 2: Replace module-only active race state with persisted state**

Keep `setActiveRaceId` as a small compatibility wrapper if useful, but it must persist to SQLite.

Required behavior:

- `startBackgroundTracking(race.id, options)` stores an active session before starting location updates
- background task reads the active race from SQLite if the module variable is empty
- `stopBackgroundTracking()` marks the active session as stopped
- failures while starting background tracking should not leave a misleading active session

- [ ] **Step 3: Add a store-level session recovery action**

Add a store action such as:

```ts
recoverActiveTrackingSession: () => Promise<void>;
```

Behavior:

- initialize the DB
- find the most recently updated active session
- load the race through `api.getRace`
- set `currentRace`, `isTracking`, `batterySaver`, and `startTime`
- rehydrate existing local locations for that race

If the race cannot be loaded because the session is unauthorized or deleted:

- keep queued locations intact
- mark the UI as not actively tracking
- do not delete location rows

- [ ] **Step 4: Invoke recovery during app startup**

Update `apps/mobile/app/_layout.tsx` so startup runs auth initialization and tracking recovery without hiding the splash screen too early.

Guardrails:

- do not block forever if recovery fails
- log the failure
- hide the splash screen once fonts and initialization attempts complete

- [ ] **Step 5: Rehydrate when the tracking screen becomes active**

Keep the current `AppState` rehydrate path in `apps/mobile/app/tracking.tsx`, but make it use the recovered active session when `currentRace` is present.

- [ ] **Step 6: Run mobile TypeScript**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter race-tracker-mobile exec tsc --noEmit
```

Expected: PASS

- [ ] **Step 7: Commit session recovery**

```bash
git add apps/mobile/lib/tracking-service.ts apps/mobile/store/tracking.ts apps/mobile/app/_layout.tsx apps/mobile/app/tracking.tsx
git commit -m "feat(mobile): recover active tracking sessions"
```

If `apps/mobile/lib/tracking-session.ts` was created, include it in this commit.

## Task 4: Harden The Offline Location Queue

**Files:**

- Modify: `apps/mobile/lib/tracking-service.ts`
- Modify: `apps/mobile/store/tracking.ts`
- Optional create: `apps/mobile/lib/location-queue.ts`

- [ ] **Step 1: Add queue metadata to the local locations table**

Update `initDb` so existing installs are migrated safely with `ALTER TABLE` guarded by try/catch or schema checks.

Target fields:

```sql
clientPointId TEXT;
syncAttempts INTEGER NOT NULL DEFAULT 0;
lastSyncError TEXT;
updatedAt TEXT;
```

Add a uniqueness guard:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_locations_race_timestamp_unique
ON locations(raceId, timestamp);
```

If the app generates a `clientPointId`, add:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_locations_client_point_id_unique
ON locations(clientPointId);
```

- [ ] **Step 2: Make location inserts idempotent**

Update background location inserts to use `INSERT OR IGNORE` or equivalent Expo SQLite-supported semantics.

Required behavior:

- duplicate foreground/background points do not create duplicate DB rows
- duplicate inserts do not throw and stop the background task
- every new row gets `updatedAt`

- [ ] **Step 3: Make sync status safe**

Update `syncPendingLocations`:

- select queued points ordered by timestamp
- build the filtered batch for network delivery
- only mark rows synced after `api.sendLocationsBatch` succeeds
- increment `syncAttempts` and store `lastSyncError` when delivery fails
- avoid string-interpolating ids directly into SQL; use placeholders or a small helper that creates placeholders from numeric row ids

Important: If all selected points are locally compacted as redundant, mark them synced only after this compaction decision is explicit and documented in code. Do not mark points synced after a network failure.

- [ ] **Step 4: Rehydrate UI from the DB queue**

Ensure `rehydrateFromLocalDb` loads all race locations needed for the local route, not only synced points.

The tracking screen should continue to show the runner's local path even while offline.

- [ ] **Step 5: Run mobile TypeScript**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter race-tracker-mobile exec tsc --noEmit
```

Expected: PASS

- [ ] **Step 6: Commit queue hardening**

```bash
git add apps/mobile/lib/tracking-service.ts apps/mobile/store/tracking.ts
git commit -m "fix(mobile): harden offline location queue"
```

If `apps/mobile/lib/location-queue.ts` was created, include it in this commit.

## Task 5: Add Deferrable Background Sync And Runtime Readiness

**Files:**

- Modify: `apps/mobile/app.json`
- Modify: `apps/mobile/lib/tracking-service.ts`
- Create: `apps/mobile/lib/mobile-runtime.ts`
- Create: `apps/mobile/lib/notifications.ts`
- Create: `apps/mobile/eas.json`

- [ ] **Step 1: Register a deferrable background sync task**

Use `expo-background-task` with `expo-task-manager` to add a catch-up sync task.

Behavior:

- task reads the active tracking session from persisted local state
- task calls `syncPendingLocations(activeRaceId)`
- task returns the SDK-supported success/failure result
- task does not start location tracking
- task does not assume immediate execution

Name the task clearly, for example:

```ts
export const LOCATION_SYNC_TASK = 'LOCATION_SYNC_TASK';
```

- [ ] **Step 2: Schedule background sync when tracking starts**

In `startBackgroundTracking` or the store's `startTracking` flow:

- register the background sync task if needed
- schedule it with the minimum practical interval supported by the SDK
- keep the existing foreground interval for active tracking while the app is open

Do not rely on `expo-background-task` for live race timing. It is only a catch-up mechanism.

- [ ] **Step 3: Stop or pause background sync when tracking stops**

When tracking stops:

- flush pending points once
- mark the active session stopped
- unregister or leave the sync task according to the SDK's recommended behavior
- avoid clearing queued failed points

- [ ] **Step 4: Add OTA update readiness**

Update `apps/mobile/app.json` with a safe runtime version policy:

```json
{
  "expo": {
    "runtimeVersion": {
      "policy": "appVersion"
    },
    "updates": {
      "checkAutomatically": "ON_LOAD",
      "fallbackToCacheTimeout": 0
    }
  }
}
```

Do not add a fake `updates.url` or fake `extra.eas.projectId`.

Create `apps/mobile/eas.json`:

```json
{
  "cli": {
    "version": ">= 13.0.0"
  },
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal",
      "channel": "development"
    },
    "preview": {
      "distribution": "internal",
      "channel": "preview"
    },
    "production": {
      "channel": "production"
    }
  },
  "submit": {
    "production": {}
  }
}
```

- [ ] **Step 5: Add notification readiness utility**

Create `apps/mobile/lib/notifications.ts` with:

- Android notification channel setup
- local notification permission helper
- no backend token registration
- no premium fan notification rules

Keep the utility unused unless startup wiring is minimal and safe. Backend push-token registration belongs to a later plan.

- [ ] **Step 6: Add runtime diagnostics utility**

Create `apps/mobile/lib/mobile-runtime.ts` to expose safe diagnostic values for future support screens/logs:

- Expo application version
- native runtime version from `expo-updates` when available
- update id when available
- whether updates are enabled

Do not create a new UI surface in this task.

- [ ] **Step 7: Run validation**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter race-tracker-mobile exec tsc --noEmit
pnpm --filter race-tracker-mobile lint
pnpm --filter race-tracker-mobile exec expo-doctor
```

Expected: PASS or only documented Expo Doctor warnings.

- [ ] **Step 8: Commit background/update readiness**

```bash
git add apps/mobile/app.json apps/mobile/eas.json apps/mobile/lib/tracking-service.ts apps/mobile/lib/mobile-runtime.ts apps/mobile/lib/notifications.ts
git commit -m "feat(mobile): add background sync and update readiness"
```

## Task 6: Update Documentation And Manual Verification

**Files:**

- Modify: `apps/mobile/README.md`
- Modify: `README.md`
- Modify: `DEVELOPMENT_GUIDE.md`

- [ ] **Step 1: Update the mobile README runtime section**

Document:

- Expo SDK 54 baseline
- Node `>=20.19`
- `pnpm --filter race-tracker-mobile start`
- development build recommendation
- Expo Go limitations for push notifications and native behavior

- [ ] **Step 2: Document offline tracking behavior**

In `apps/mobile/README.md`, explain:

- GPS points are queued in SQLite
- active tracking sessions are persisted locally
- failed sync keeps points queued
- deferrable background sync is a catch-up mechanism and not a guarantee of immediate delivery
- users must grant foreground/background location permissions for race tracking

- [ ] **Step 3: Update root docs**

Update `README.md` and `DEVELOPMENT_GUIDE.md` with:

- the mobile SDK 54 foundation status
- the mobile validation commands
- the EAS Update readiness note
- what remains deferred: SDK 55/New Architecture, backend push tokens, premium mobile fan features, RFID/bib ingest

- [ ] **Step 4: Run final validation**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter race-tracker-mobile exec tsc --noEmit
pnpm --filter race-tracker-mobile lint
pnpm --filter race-tracker-mobile exec expo-doctor
pnpm contracts:validate
```

Expected: PASS or explicitly documented Expo Doctor warnings.

- [ ] **Step 5: Manual smoke test on a development build**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm --filter race-tracker-mobile start -- --clear
```

Then verify on a development build where possible:

- app opens
- login still works
- My Races can open tracking
- tracking can start after location permission grants
- background/foreground transition does not lose the active session
- stopping tracking flushes pending points
- if network is disabled, queued points remain local and retry later

- [ ] **Step 6: Commit docs**

```bash
git add apps/mobile/README.md README.md DEVELOPMENT_GUIDE.md
git commit -m "docs(mobile): document runtime modernization foundation"
```

## Final Review Checklist

Before marking this plan complete:

- [ ] `git status --short` is clean except intentional local environment files
- [ ] SDK 54 package alignment is visible in `apps/mobile/package.json`
- [ ] root Node baseline is `>=20.19`
- [ ] `pnpm --filter race-tracker-mobile exec tsc --noEmit` passes
- [ ] `pnpm --filter race-tracker-mobile lint` passes
- [ ] `pnpm --filter race-tracker-mobile exec expo-doctor` passes or warnings are documented
- [ ] active race recovery does not depend on a module-level variable
- [ ] failed location sync attempts do not delete or mark unsent points synced
- [ ] docs clearly separate this SDK 54 foundation from future SDK 55/New Architecture work

## Known Risks

- Native map dependencies may still require targeted compatibility fixes after SDK 54 alignment. Fix only the compatibility issues required to build and run.
- Background task timing is controlled by iOS/Android and cannot be used for immediate race timing guarantees.
- EAS Update cannot be fully activated without a real Expo project id/update URL. This plan intentionally scaffolds readiness and documents the final account-backed setup step.
- Push notifications need backend token registration and preference management in a later plan.
