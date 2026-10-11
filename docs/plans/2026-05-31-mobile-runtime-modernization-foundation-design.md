# StrideX Mobile Runtime Modernization Foundation Design

## Status

Approved on 2026-05-31 as the next modernization phase after the web routing/data foundation and backend observability foundation.

This document is the design/spec artifact. It is not the implementation plan.

## Problem

The mobile app already uses Expo Router, Expo Location, SQLite, Zustand, and native maps, but the runtime is uneven:

- `apps/mobile` is on Expo SDK 53 / React Native 0.79 while some Expo packages and tooling are already drifting toward SDK 56.
- The mobile TypeScript check fails, including missing tracking-store types and an Expo Location option that does not match the installed API.
- Background tracking stores the active race only in memory, so cold starts, process restarts, and OS-managed background task launches can lose the race context.
- Offline GPS sync is functional but not yet robust enough for race-day reliability, retry visibility, or restart-safe recovery.
- OTA update, development-build, and notification readiness are not first-class yet, even though they are important for operating a mobile race-day product.

The goal is to modernize the runtime and offline foundation without redesigning the app or changing product scope.

## Current Baseline

`apps/mobile/package.json` currently declares:

- Expo `~53.0.0`
- React Native `0.79.0`
- React `19.0.0`
- `expo-sqlite` and `expo-task-manager` package versions that are not aligned with SDK 53
- no `expo-updates`, `expo-notifications`, `expo-background-task`, `eas.json`, `.nvmrc`, or `.node-version`

Local validation found:

```bash
pnpm --filter race-tracker-mobile exec tsc --noEmit
```

fails because:

- `rehydrateFromLocalDb` exists in the store implementation but is missing from `TrackingState`
- untyped SQLite rows are being queried with generic arguments against an `any` database handle
- `pausesLocationUpdatesAutomatically` should be aligned to the Expo Location option supported by the installed SDK
- the current TypeScript version is too old for the Expo SDK package baseline in the lockfile

## External Platform Baseline

As of 2026-05-31, Expo SDK 55 is available, but SDK 54 is the recommended bridge release for this app.

Why SDK 54:

- Expo SDK 54 targets React Native 0.81 and React 19.1.
- Expo SDK 54 is the final Expo release that still includes Legacy Architecture support, which is useful while validating native map/location dependencies.
- SDK 54 raises the Android target to API 36 and enables Android edge-to-edge behavior, so the app should explicitly test layout/safe-area behavior after the upgrade.

Why not SDK 55 in this phase:

- Expo SDK 55 targets React Native 0.83 and React 19.2.
- SDK 55 drops Legacy Architecture support, making New Architecture mandatory.
- SDK 55 has a documented Expo Go transition period, and the project should already move toward development builds before depending on that path.

Primary references:

- Expo SDK version reference: https://docs.expo.dev/versions/v54.0.0/
- Expo SDK 54 changelog: https://expo.dev/changelog/sdk-54
- Expo SDK 55 changelog: https://expo.dev/changelog/sdk-55
- Expo New Architecture guide: https://docs.expo.dev/guides/new-architecture/
- Expo Updates SDK 54 docs: https://docs.expo.dev/versions/v54.0.0/sdk/updates/
- Expo Notifications SDK 54 docs: https://docs.expo.dev/versions/v54.0.0/sdk/notifications/
- Expo BackgroundTask docs: https://docs.expo.dev/versions/latest/sdk/background-task/

## Recommendation

Use an SDK 54 bridge modernization.

Modernize in place:

- align the mobile runtime and Expo packages to SDK 54
- fix TypeScript/runtime API drift
- keep Expo Router and the existing page/component architecture
- keep SQLite as the local-first tracking anchor
- persist active tracking sessions so background tasks can recover after app restarts
- add a deferrable background sync task for catch-up delivery, not as a replacement for live location updates
- add EAS Update/development-build readiness without requiring a new Expo account or production store release in this implementation pass

Do not:

- jump directly to SDK 55
- eject from Expo
- redesign the mobile UI
- add premium fan features
- change backend tracking contracts unless the existing mobile client is already incompatible

## Rejected Approaches

### 1. Jump directly to Expo SDK 55

Pros:

- moves to the newest stable Expo runtime
- forces New Architecture readiness sooner
- exposes newer Expo Router/native capabilities

Cons:

- higher native dependency risk for maps, location, camera, and Reanimated
- no Legacy Architecture fallback
- Expo Go transition makes this less suitable as a first stabilization pass

### 2. Fix only TypeScript without upgrading Expo

Pros:

- smallest possible change
- fastest local validation path

Cons:

- leaves the runtime modernization goal mostly unfinished
- keeps the dependency graph in a drifted state
- delays SDK/new architecture readiness

### 3. Rewrite tracking as a new local-first subsystem

Pros:

- could produce a cleaner long-term sync model

Cons:

- too much change for this phase
- higher risk to existing race-day tracking behavior
- likely to pull backend ingest and telemetry design into the mobile runtime task

## Target Architecture

### Runtime

`apps/mobile` should target Expo SDK 54 with SDK-aligned Expo modules, React Native, React, TypeScript, and lint tooling. The root Node requirement should be raised to match Expo SDK 54's documented Node baseline.

### Tracking State

Tracking remains Zustand-driven in foreground UI, but the active session source of truth should be persisted locally:

- active race id
- tracking start time
- battery-saver mode
- session status
- last known sync status

The background task should read the active race id from persisted local state, not a module-level variable.

### Offline Queue

SQLite remains the queue for GPS points. The schema should become more explicit:

- unique client point id or unique race/timestamp constraint to reduce duplicate foreground/background points
- sync status fields that distinguish queued, sent, and failed records
- retry metadata for diagnosis and future observability

The sync worker should only mark points as synced after successful delivery or after an intentional local compaction decision that is safe to lose.

### Background Work

Expo Location background updates remain the primary tracking mechanism.

`expo-background-task` should be used only for deferrable catch-up sync of queued points. This is important because platform background tasks are opportunistic and cannot guarantee immediate execution.

### Updates And Release Readiness

Add OTA update readiness in a way that is safe for later EAS setup:

- add `expo-updates`
- define `runtimeVersion`
- document EAS Update setup and channel expectations
- avoid inventing a fake EAS project id or update URL

Development builds should become the recommended mobile testing path because remote push notifications and native module behavior cannot be fully validated in Expo Go.

### Notifications

This phase should add notification readiness, not a full notification product:

- add `expo-notifications`
- document that Android remote notifications require a development build from SDK 53 onward
- add a minimal mobile notification utility only if needed by implementation
- defer backend push-token registration and premium fan notification features to later plans

## Error Handling

The app should surface race-day-critical failures clearly:

- missing foreground/background location permission
- background tracking start failure
- database initialization failure
- queued sync retry failure
- API auth/session failure during sync

Errors should not silently clear queued points. Failed sync attempts should keep points in SQLite for later retry.

## Testing Strategy

The implementation should validate:

- mobile package graph is SDK-aligned
- TypeScript passes
- lint passes
- Expo Doctor passes or produces only explicitly documented warnings
- tracking-service unit-level logic can persist/recover active sessions where practical
- manual smoke test path exists for:
  - login
  - start tracking
  - background/foreground transition
  - stop tracking
  - offline queued sync retry

## Success Criteria

This phase is complete when:

- `pnpm --filter race-tracker-mobile exec tsc --noEmit` passes
- `pnpm --filter race-tracker-mobile lint` passes
- SDK 54 package alignment is complete
- active tracking session recovery no longer depends on a module-level variable
- queued GPS points are not lost on failed sync
- mobile docs describe the SDK 54 baseline, development-build expectation, EAS Update readiness, and offline tracking model

## Follow-Up Work

Separate future plans should cover:

- Expo SDK 55 / mandatory New Architecture migration
- backend push-token registration and user notification preferences
- premium athlete/fan mobile features
- geospatial telemetry and route-matching modernization
- RFID/bib ingest integration
- mobile observability with OpenTelemetry/Sentry-style crash/error reporting
