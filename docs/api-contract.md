# API Contract Consistency

This repository uses a shared contract package to keep backend, web, and mobile in sync.

## Source Of Truth

- Contract config: `packages/contracts/contract.json`
- Shared runtime constants: `packages/contracts/index.js` (`@race-tracker/contracts`)

## Consumers

- Web API client uses `API_CONTRACT.web.apiBasePath`.
- Mobile API client uses `getVersionedApiBase(API_ROOT)`.
- Backend auth allowlist in `SecurityConfig` builds routes from `racetracker.api.version`.

## Validation

Run:

```bash
pnpm contracts:validate
```

This checks:

- `racetracker.api.version` in backend matches contract `apiVersion`.
- Backend security routes are not hardcoded to `/v1`.
- Backend public spectator allowlist uses the versioned `apiPrefix`.
- Web/mobile API clients import and use `@race-tracker/contracts`.
- Shared contract package values are synchronized with `contract.json`.
- Public endpoint keys stay aligned across `contract.json`, `index.js`, and `index.d.ts`.

## Public Spectator Endpoint Keys

The shared contract publishes these anonymous spectator paths:

- Current consumer: the public spectator web experience.
- Future consumers may reuse these keys if they expose the same anonymous spectator surface.
- Responses on these endpoints must remain limited to spectator-safe, public-safe fields only.

- `publicRaceHub`: `/public/races/:raceId/hub`
- `publicRaceLive`: `/public/races/:raceId/tracking/live`
- `publicRaceSearch`: `/public/races/:raceId/tracking/search`
- `publicAthleteTracking`: `/public/races/:raceId/tracking/athletes/:participantId`

## RFID Bridge Endpoints

The RFID bridge endpoints are backend-owned and not currently published in the shared web/mobile contract package because they are not consumed by the public web or mobile apps.

- Admin reader management uses normal JWT/operator authorization:
- `POST /api/v1/races/{raceId}/rfid/readers`
- `GET /api/v1/races/{raceId}/rfid/readers`
- `PATCH /api/v1/races/{raceId}/rfid/readers/{readerId}/revoke`
- Device ingest uses `X-RFID-Reader-Key`:
- `POST /api/v1/rfid/read-events`

If web/mobile clients later manage readers or display reader health, add these paths to `packages/contracts` before wiring those clients.

## Changing API Version

1. Update `packages/contracts/contract.json` (`apiVersion`, paths if needed).
2. Update backend `racetracker.api.version` in `apps/backend/src/main/resources/application.yml`.
3. Run `pnpm contracts:validate`.
4. Run app builds/tests.
