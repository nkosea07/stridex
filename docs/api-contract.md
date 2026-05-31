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
- Web/mobile API clients import and use `@race-tracker/contracts`.
- Shared contract package values are synchronized with `contract.json`.

## Changing API Version

1. Update `packages/contracts/contract.json` (`apiVersion`, paths if needed).
2. Update backend `racetracker.api.version` in `apps/backend/src/main/resources/application.yml`.
3. Run `pnpm contracts:validate`.
4. Run app builds/tests.

