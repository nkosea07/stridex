# API Contract Consistency

This repository uses a shared contract package to keep backend, web, and mobile in sync.

## Source Of Truth

- Contract config: `packages/contracts/contract.json`
- Shared runtime constants: `packages/contracts/index.js` and types in `index.d.ts`
  (`@race-tracker/contracts`)

`contract.json` holds the API version, the web/mobile base paths, the WebSocket path
(`web.wsTrackingPath`, currently `/api/ws-tracking`), legal/consent versions, i18n locales,
and every REST endpoint template under `endpoints` (route parameters use `:name`).

## Consumers

- Web (`apps/web/src/lib/api.ts`) uses `API_CONTRACT.web.apiBasePath` and resolves every path
  from `API_CONTRACT.endpoints` with `buildContractPath`.
- Mobile (`apps/mobile/lib/api.ts`) uses `getVersionedApiBase(API_ROOT)` and the same endpoint keys.
- Backend controllers map `/v${racetracker.api.version}/...`; `SecurityConfig` builds its anonymous
  allowlist from the same version.

Neither client may contain a hardcoded `/api/vN` path or an endpoint string that is not declared
in the contract.

## Validation

Run:

```bash
pnpm contracts:validate
```

This checks:

- `racetracker.api.version` in the backend matches contract `apiVersion`.
- Backend security routes are not hardcoded to `/v1`, and the public spectator allowlist uses the
  versioned prefix.
- Every backend controller route shape is declared in `contract.endpoints`, and every contract
  endpoint is served by a controller (drift in either direction fails).
- Web and mobile import `@race-tracker/contracts`, reference only declared endpoint keys, and use
  no undeclared literal paths.
- `index.js` and `index.d.ts` stay synchronized with `contract.json`.

CI runs this before the web build.

## Public Spectator Endpoints

Anonymous `GET` routes under `/public/races/**` (for example `publicRaceHub`,
`publicRaceLive`, `publicRaceLiveFeed`, `publicRaceSearch`, `publicAthleteTracking`,
`publicAthleteTrack`, `publicRaceHeatmap`, `publicRaceResults`, `publicRaceMedia`,
`publicRaceRoute`) serve the spectator web experience.

Responses must stay spectator-safe: no emails, phone numbers, emergency or medical data,
device tokens, internal notes, or audit fields. Per-athlete GPS tracks honor the participant's
tracking setting, and the public heatmap excludes tracking-disabled participants and is limited
to coarse (≤ 4 decimal place) cells.

## RFID Bridge Endpoints

Reader administration and device ingest are declared in the contract:

- Admin (JWT, race-scoped): `raceRfidReaders` (`/races/:raceId/rfid/readers`),
  `raceRfidOverview`, `raceRfidReaderRevoke`.
- Device ingest (`X-RFID-Reader-Key` header): `rfidReadEvents` (`/rfid/read-events`, at most
  200 reads per request) and `rfidReaderHeartbeat` (`/rfid/readers/heartbeat`).

A reader API key is returned once at creation and stored only as a SHA-256 hash.

## Changing API Version Or Paths

1. Update `packages/contracts/contract.json` (`apiVersion`, endpoint templates).
2. Mirror the change in `packages/contracts/index.js` and `index.d.ts`.
3. Update backend `racetracker.api.version` in `apps/backend/src/main/resources/application.yml`
   and the affected controllers.
4. Update both clients, then run `pnpm contracts:validate` and the app builds/tests.
