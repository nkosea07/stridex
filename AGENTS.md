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

CI is `.github/workflows/pilot-validation.yml` (runs on push and PR). Reproduce locally.

From the root (after `pnpm install --frozen-lockfile`):
- `pnpm contracts:validate` (gates the web build — run it before/with any web build)
- `pnpm --filter web lint && pnpm --filter web typecheck && pnpm --filter web test && pnpm --filter web build`
- `pnpm --dir apps/mobile lint`, `pnpm --dir apps/mobile exec tsc --noEmit`, `pnpm --dir apps/mobile test`
- `pnpm --filter rfid-gateway test`

Java 17, each command from its own app directory:
- `apps/backend`: `mvn -B test` (focused: `mvn -Dtest=ClassName test`)
- `apps/rfid-reader-demo`: `mvn -B test`
- `apps/rfid-reader-impinj`: `mvn -B test`

CI runs the JS job on Node 22; the local floor is Node 20.19 (`.node-version`).

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

## Engineering standards

Always apply production-grade best practices in every implementation, regardless of how
small the change. Load the matching skill before working in an area:
`java-spring-backend` (Java backend), `postgresql-code-review` (database),
`shadcn-ui` / `frontend-design` / `vercel-react-best-practices` (frontend). Prefer the
smallest complete change that follows existing patterns.

- **General:** read the surrounding code first; reuse existing utilities; no unrelated
  rewrites or reformatting; no comments unless asked; handle errors explicitly; never claim
  a test/build passed unless it actually ran.
- **Java backend:** constructor injection, no field injection; keep controller (HTTP) →
  service (logic) → repository (data) layering; use DTOs + MapStruct mappers, never expose
  entities; explicit `@Transactional` boundaries; validate inputs at the edge; throw domain
  exceptions (ApiException/ResourceNotFoundException/ConflictException, `AccessDeniedException`
  for authz) — never leak internal exception messages; enforce authorization/ownership
  server-side; add focused JUnit/Mockito tests.
- **Database:** Flyway is the only schema source — add a new versioned migration for every
  change and never edit an applied one; keep Hibernate `ddl-auto: validate` passing; snake_case
  columns; explicit FKs and indexes for query paths; use parameterized queries (no string
  concatenation); avoid unbounded/`SELECT *` full-table reads — paginate or stream; watch
  high-volume tables (`gps_locations`, audit/events) for partitioning/retention.
- **Frontend:** TypeScript strict; React Router 7 framework mode; server state via the
  established data layer, not ad-hoc `useState` for remote data; no hardcoded API/WS paths —
  resolve them from `@race-tracker/contracts`; reuse `apps/web/src/components/ui` (shadcn)
  primitives; responsive, dark-mode-aware, accessible (labels, roles, keyboard, `aria-live`);
  keep bundle/map-heavy views lazy where practical.
- **API/contract:** the contract is the source of truth — change `contract.json` + version and
  both clients together, then run `pnpm contracts:validate`. Public spectator endpoints stay
  PII-free.
- **Internationalization (required for all new work):** locales are defined once in
  `contract.json` (`i18n.supportedLocales`) and shared strings live in `packages/i18n`.
  Never hardcode user-facing text or dates/numbers/currency in web or mobile — use
  `t('<namespace>.<key>')` and the shared format helpers (`formatDate`, `formatDateTime`,
  `formatNumber`, `formatCurrency`, `formatDuration`, `formatPace`). Add every new key to the
  `en` catalog (and seed other locales as translations land); run `pnpm i18n:check` (CI-gated).
  Backend user-facing text (emails/notifications) is localized via `MessageSource`; API errors
  expose stable codes. Adding a language = one catalog + the contract list; do not branch code
  on locale.

## Agent workflow

OpenCode subagents live in `.opencode/agents/` (tracked in git). The primary agent
orchestrates non-trivial work through them in this order:

- `architect` — first, for diagnosis and a precise implementation plan. Read-only (`edit` denied).
- `developer` — implement an approved plan; run focused tests, then the wider Verify set.
- `tester` — independently verify the change; fix straightforward defects only.
- `reviewer` — final independent review (correctness, security, contracts, regressions).

Rules:
- Read the relevant `apps/<app>/README.md` before changing that app (backend/mobile/gateway
  READMEs load via `instructions` in `opencode.jsonc`).
- Verify against the Verify section above; never claim a test/build passed unless it ran.
- Load a skill via the skill tool when the task matches one (e.g. `frontend-design`,
  `postgresql-code-review`). Skills are user-global (`~/.agents/skills`, `~/.claude/skills`);
  the repo defines none.
