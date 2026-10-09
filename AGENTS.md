# Race Tracker Platform

Monorepo for race/marathon tracking: live GPS, RFID checkpoint timing, results, and web/mobile surfaces.
Per-app docs: `apps/backend/README.md`, `apps/mobile/README.md`, `apps/rfid-gateway/README.md`, `docs/` (`DEVELOPMENT_GUIDE.md` is partly stale — trust configs/scripts over it).

## Layout & boundaries

- pnpm workspace (`apps/*`, `packages/*`). Package names: `web`, `race-tracker-mobile`, `rfid-gateway`, `@race-tracker/contracts`.
- Maven-only (not pnpm filters): `apps/backend`, `apps/rfid-reader-demo`, `apps/rfid-reader-impinj`.
- Backend Java packages: `com.racetracker.{controller,service,repository,model,dto,mapper,security,config,websocket}`.
- Web is React Router 7 framework mode (`ssr:false`). Routes are registered in `apps/web/app/routes.ts` with modules in `apps/web/app/routes/`; most UI lives in `apps/web/src/`; `@` aliases `apps/web/src`.
- Stale scaffold to ignore: repository-root `app/`, `components/`, `hooks/`, `lib/`, `components.json`, `postcss.config.mjs`, and root `tsconfig.json` are leftover v0/Next.js+shadcn files. They are not in the workspace and are not imported by `apps/web`. Edit web components in `apps/web/src/components/ui`, not root `components/ui`.

## Toolchain

- Node 20.19+ (`.node-version`), pnpm 10.29.2, Java 17 (backend and reader adapters), Docker.
- Backend uses servlet context-path `/api` on port 8081, so routes are `/api/v1/**` and actuator is `/api/actuator/**`.

## Setup

- `pnpm install` at the root.
- Backend + local DB: `cd apps/backend && docker compose up -d db redis && mvn spring-boot:run`. Local PostGIS is on host port **5435** (`apps/backend/.env`); Redis on 6379.
- Or `pnpm dev:stack` / `pnpm dev:down` (db + backend + web).
- Web dev: `pnpm --filter web dev` (port 5173, proxies `/api` → `127.0.0.1:8081`).
- Mobile: `pnpm --filter race-tracker-mobile start`.

## Verify (matches CI)

From the root:
- `pnpm contracts:validate`
- `pnpm --filter web lint && pnpm --filter web typecheck && pnpm --filter web test && pnpm --filter web build`
- `pnpm --dir apps/mobile lint`, `pnpm --dir apps/mobile exec tsc --noEmit`, `pnpm --dir apps/mobile test`
- `pnpm --filter rfid-gateway test`
- Backend (Java 17, from `apps/backend`): `mvn -B test`; focused: `mvn -Dtest=ClassName test`.

`pnpm contracts:validate` gates the web build, so run it before/with any web build.

## API contract (do not hand-edit clients)

`packages/contracts/contract.json` is the source of truth, mirrored in `packages/contracts/index.js` and `index.d.ts`. Web (`apps/web/src/lib/api.ts`) and mobile (`apps/mobile/lib/api.ts`) import `@race-tracker/contracts`. `pnpm contracts:validate` cross-checks the contract against backend `racetracker.api.version` (`apps/backend/src/main/resources/application.yml`), `SecurityConfig`, and both clients. Change the contract and version together, then validate.

## Database

- Flyway is authoritative: `apps/backend/src/main/resources/db/migration` (`V1__baseline.sql` … current). Add a new versioned migration for every schema change; never edit an applied migration.
- `apps/backend/database/schema.sql` is reference only — never run it to bootstrap a database.
- Hibernate `ddl-auto: validate` (local `dev` profile uses `update`).
- `FlywayPostgresMigrationSmokeTest` runs only when `FLYWAY_TEST_URL`, `FLYWAY_TEST_USERNAME`, and `FLYWAY_TEST_PASSWORD` are set (CI provides a PostGIS service); otherwise it is skipped.

## Environment & deployment

- Backend DB vars are `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` — not `DATABASE_URL` (`DEVELOPMENT_GUIDE.md` still shows old names and a stale JWT secret).
- Web build-time config: `VITE_API_URL`, `VITE_MAPBOX_TOKEN`. Mobile: `EXPO_PUBLIC_*` only — never put server secrets in client variables.
- Pilot: `compose.pilot.yml` with `.env.pilot` copied from `.env.pilot.example`. Production requires a non-default `JWT_SECRET`, base64 32-byte `MFA_ENCRYPTION_KEY`, HTTPS `FRONTEND_URL`, secure cookies, and `RFID_SIMULATION_ENABLED=false`.
- Ops/procedure references: `docs/pilot-operations-runbook.md`, `docs/rfid-race-pilot-runbook.md`. Never commit populated `.env` files (gitignored; `apps/backend/.env` is local-only and untracked).

## Repo conventions

- Public spectator endpoints (`/api/v1/public/races/**`) are anonymous and must stay spectator-safe/PII-free: no emails, phones, emergency/medical data, device tokens, or audit fields.
- RFID reader ingest authenticates with `X-RFID-Reader-Key`; reader admin routes use JWT. A reader API key is shown once at creation and stored only as a SHA-256 hash.
- CI is `.github/workflows/pilot-validation.yml`: JS job (order above) → backend on Java 17 + PostGIS service → Docker image builds.
