# Race Tracker Platform

A comprehensive race/marathon tracking system with live GPS tracking, checkpoint management, and real-time results.

## Architecture

This is a monorepo containing six applications:

```
├── apps/
│   ├── backend/       # Java Spring Boot API (PostgreSQL)
│   ├── rfid-gateway/  # Edge gateway for race timing readers
│   ├── rfid-reader-demo/ # Compact UHF demo reader adapter
│   ├── rfid-reader-impinj/ # Impinj reader control adapter skeleton
│   ├── web/           # React Vite Web App
│   └── mobile/        # React Native (Expo) Mobile App
└── packages/          # Shared packages (if needed)
```

## Applications

### Backend (Spring Boot + PostgreSQL)
**Location:** `apps/backend/`

The backend API built with Java Spring Boot, providing:
- RESTful API endpoints
- WebSocket support for real-time tracking
- PostgreSQL database with full schema
- JWT authentication
- Role-based access control

**Tech Stack:**
- Java 21
- Spring Boot 3.5
- Spring Security + JWT
- Spring Data JPA
- PostgreSQL 15 + PostGIS
- WebSocket (STOMP)
- Actuator + Micrometer/Prometheus

[View Backend README](apps/backend/README.md)

### Web Application (React Vite)
**Location:** `apps/web/`

Web surface for both public spectators and authenticated race operations:
- Public race hub and athlete tracking pages for anonymous spectators
- Race management (create, edit, configure)
- Participant management
- Live tracking map view
- Checkpoint monitoring
- Results and analytics

**Tech Stack:**
- React 19
- Vite
- TypeScript
- Tailwind CSS
- Mapbox GL JS
- Recharts

Modernization status:
- `apps/web` remains a Vite-based app, now wired through React Router route modules.
- The first modernization target is the public spectator web surface, especially `/race/:raceId` and `/race/:raceId/athlete/:participantId`.
- Route modules live under `apps/web/app/` and continue to render most UI from `apps/web/src/`.
- Deeper prerender and SSR work is intentionally deferred to a later plan.

### RFID Gateway (Node.js Edge Service)
**Location:** `apps/rfid-gateway/`

Edge service for checkpoint timing hardware:
- Accepts normalized local RFID read batches over HTTP
- Persists pending batches to a local spool directory for restart safety
- Flushes buffered reads to the backend RFID ingest API
- Sends periodic reader/gateway heartbeats with backlog and hardware metadata

**Tech Stack:**
- Node.js 20+
- Built-in HTTP server and `fetch`
- File-backed spool queue

### Demo Reader Adapter (Spring Boot Edge Service)
**Location:** `apps/rfid-reader-demo/`

Compact-reader edge profile for demos and narrow read zones:
- Accepts local compact-reader events at `POST /reader-events`
- Normalizes them into the same gateway payload used by the Impinj adapter
- Batches and forwards reads to the local `rfid-gateway`
- Keeps demo readers on the same backend inventory and heartbeat model as production readers

**Tech Stack:**
- Java 17 / Spring Boot 3.5
- Scheduled batching and forwarding
- REST client to local gateway

### Impinj Reader Adapter (Spring Boot Edge Service)
**Location:** `apps/rfid-reader-impinj/`

Hardware-control seam for Impinj readers:
- Hosts the pluggable reader source lifecycle
- Normalizes inventory reads into the platform RFID payload shape
- Batches and forwards reads to the local `rfid-gateway`
- Defaults to a mock reader mode until the real Octane SDK client is dropped in

**Tech Stack:**
- Java 17 / Spring Boot 3.5
- Scheduled batching and forwarding
- REST client to local gateway

### Mobile App (React Native Expo)
**Location:** `apps/mobile/`

Cross-platform mobile app for athletes:
- Race discovery and registration
- Live GPS tracking during races
- QR code checkpoint scanning
- Personal results and stats
- Push notifications

**Tech Stack:**
- React Native 0.81 (Expo SDK 54)
- React 19.1
- Expo Router
- Zustand
- React Native Maps
- Expo Location & Camera
- Expo SQLite
- Expo BackgroundTask
- Expo Updates / EAS Update readiness

[View Mobile README](apps/mobile/README.md)

## Product Surfaces

- `apps/web`: serves both public spectators and authenticated/admin workflows. Public Phase 1 routes include `/race/:raceId` and `/race/:raceId/athlete/:participantId`.
- `apps/mobile`: remains the richer logged-in experience for athletes. Premium fan-oriented mobile features are later phases and out of scope for this foundation.
- `apps/backend`: serves protected admin/athlete APIs plus anonymous public spectator read endpoints, with `/api/actuator` health/info/prometheus management endpoints for deployment and race-day operations.

Backend modernization status:
- `apps/backend` now runs on Spring Boot 3.5 with Actuator and Micrometer observation support.
- Public tracking and tracking ingest hot paths use stable `@Observed` names for metrics/observation.
- `/api/actuator/health/**` and `/api/actuator/info` are public; `/api/actuator/prometheus` requires authentication.
- PostGIS-backed `geography(Point, 4326)` query columns and GiST indexes exist for race, checkpoint, and GPS location proximity work while decimal lat/lng fields remain the API contract.
- External OpenTelemetry exporter setup and telemetry partitioning remain later modernization phases.

Mobile modernization status:
- `apps/mobile` targets Expo SDK 54 as the bridge runtime before any SDK 55/New Architecture-only migration.
- Active tracking sessions and queued GPS points are persisted in SQLite so normal app lifecycle interruptions do not discard race context.
- `expo-background-task` is configured only for opportunistic catch-up sync; live race timing still depends on Expo Location foreground/background updates.
- EAS build channels and `expo-updates` runtime-version policy are scaffolded, but a real Expo project id/update URL must be added when EAS Update is activated for an account.
- Backend push-token registration, premium mobile fan features, and SDK 55 migration remain later phases.

## Getting Started

### Prerequisites
- Node.js 20.19+
- Java 21
- PostgreSQL 15 with PostGIS available
- pnpm

### Quick Start

1. **Clone and install dependencies:**
```bash
# Install all dependencies
pnpm install
```

2. **Start the backend:**
```bash
cd apps/backend
docker-compose up -d  # Start PostgreSQL
mvn spring-boot:run
```

3. **Start the web app:**
```bash
cd apps/web
pnpm dev
```

4. **Start the mobile app:**
```bash
pnpm --filter race-tracker-mobile start
```

## Features

### Live GPS Tracking
- Real-time athlete location tracking
- Route visualization on maps
- Route playback (replay recorded GPS breadcrumbs with play/pause/scrub/speed)
- Race heat maps (server-side grid aggregation of tracking points)
- Pace and distance calculations
- Estimated finish time predictions
- Spectator favorites (follow athletes) with in-app and WebSocket alerts
- GPS drift correction: conservative ingest filtering (accuracy, implausible speed, non-monotonic time) plus exponential smoothing; raw points are always retained

Live tracking endpoints: `GET /api/v1/public/races/{raceId}/tracking/athletes/{participantId}/track` (playback breadcrumbs), `GET /api/v1/public/races/{raceId}/tracking/heatmap` (heat cells), and authenticated `GET/POST/DELETE /api/v1/favorites` (race-scoped athlete follows). Drift tuning is configurable via `TRACKING_ACCURACY_THRESHOLD_METERS`, `TRACKING_MAX_PLAUSIBLE_SPEED_KMH`, `TRACKING_SMOOTHING_FACTOR`, `TRACKING_MIN_DISTANCE_METERS`, `TRACKING_MAX_TRACK_POINTS`, and `TRACKING_HEATMAP_SCALE`.

### Multiple Tracking Methods
- GPS tracking from mobile app
- RFID/chip timing bridge ingest at checkpoints
- QR code scanning at checkpoints
- Manual checkpoint entry

### User Roles
- **Super Admin**: Full platform access
- **Race Admin**: Manage specific races
- **Official**: Participant, category, team, checkpoint, live tracking, RFID, results, analytics, insights, and media operations; read-only promotion visibility
- **Volunteer**: Participant, category, team, checkpoint, live tracking, results, analytics, and media operations; read-only promotion visibility
- **Athlete**: Personal registration, tracking, results, and statistics through the athlete app
- **Spectator**: Anonymous public race results and live tracking

The web operations shell exposes capabilities by role. `SUPER_ADMIN` and
`RACE_ADMIN` receive race-management controls; `OFFICIAL` and `VOLUNTEER` receive
the race operations workspace with only the tools supported by their backend role.
Athletes and spectators remain on the athlete/public surfaces and are not granted
admin routes. Server-side authorization remains authoritative for every action.

### Race Management
- Create and configure races
- Define categories and checkpoints
- Set cutoff times
- Manage registrations

### Results & Analytics
- Real-time leaderboards
- Category-specific rankings
- Split times at checkpoints
- Post-race results export

## Database Schema

The PostgreSQL database includes:
- `users` - User accounts and roles
- `races` - Race information
- `race_categories` - Race distance categories
- `participants` - Race registrations
- `checkpoints` - Race checkpoints
- `checkpoint_records` - Checkpoint crossings
- `rfid_readers` - RFID bridge reader registry and API-key hashes
- `rfid_read_events` - Raw RFID reads and processing outcomes
- `gps_locations` - GPS tracking data

PostGIS spatial query fields are managed by the backend's Flyway migrations. Keep the decimal latitude/longitude fields for API compatibility; database triggers maintain the geography columns, and GiST indexes support proximity queries such as checkpoint geofencing. Existing deployments must compare their schema and backup before manually baselining Flyway; the old date-named SQL files do not establish which changes a deployed database has received. High-volume `gps_locations` partitioning by `recorded_at` is planned as a future migration, not part of the current schema foundation.

RFID bridge ingestion is backend-first: operators register checkpoint readers, receive a one-time API key, and timing bridges submit batched reads to `/api/v1/rfid/read-events`. The backend now durably stores raw reads first, publishes them through Redis Streams for asynchronous normalization, and converts valid first-time reads into official checkpoint records via credential-based participant matching. Reader heartbeats at `/api/v1/rfid/readers/heartbeat` expose gateway health, backlog, firmware metadata, and hardware profile metadata so compact demo readers and Impinj production readers can share the same platform safely.

See `apps/backend/src/main/resources/db/migration` for the authoritative versioned schema history. `apps/backend/database/schema.sql` is retained as a schema reference and is not used to bootstrap application databases.

## API Documentation

The backend exposes RESTful APIs:

### Authentication
- `POST /api/v1/auth/register` - Register user
- `POST /api/v1/auth/login` - Login
- `GET /api/v1/auth/me` - Current user

### Races
- `GET /api/v1/races` - List races
- `POST /api/v1/races` - Create race
- `GET /api/v1/races/:id` - Race details
- `PUT /api/v1/races/:id` - Update race

### Participants
- `GET /api/v1/races/:id/participants` - List participants
- `POST /api/v1/races/:id/register` - Register for race

### Tracking
- `POST /api/v1/tracking/:raceId/location` - Send GPS location
- `POST /api/v1/tracking/:raceId/checkpoint` - Record checkpoint
- `POST /api/v1/rfid/read-events` - Submit batched RFID reads with `X-RFID-Reader-Key`
- `POST /api/v1/rfid/readers/heartbeat` - Update reader gateway health with `X-RFID-Reader-Key`
- `WebSocket /ws-tracking` - Real-time updates

### Results
- `GET /api/v1/races/:id/results` - Race results
- `GET /api/v1/results/my` - User's results

## Local Test Credentials

The following accounts are for local development and browser smoke testing only.
They are provisioned in the current local database and must never be reused in
staging or production:

| Persona | Email | Password | Role |
| --- | --- | --- | --- |
| Admin | `admin.local@racetracker.test` | `AdminTest123!` | `SUPER_ADMIN` |
| Race Admin | `raceadmin.local@racetracker.test` | `RaceAdminTest123!` | `RACE_ADMIN` |
| Official | `official.local@racetracker.test` | `OfficialTest123!` | `OFFICIAL` |
| Volunteer | `volunteer.local@racetracker.test` | `VolunteerTest123!` | `VOLUNTEER` |
| Racer | `racer.local@racetracker.test` | `RacerTest123!` | `ATHLETE` |
| Viewer | `viewer.local@racetracker.test` | `ViewerTest123!` | `SPECTATOR` |

These users use password authentication without an MFA prompt in the current
release. The three operations accounts are seeded only when the Spring `dev`
profile is active and `LOCAL_TEST_USERS_ENABLED=true`; their values come from
the ignored `apps/backend/.env` file. They are not created by Flyway migrations,
so they will not exist after the local database volume is recreated.

## Development

### Backend Development
```bash
cd apps/backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Web Development
```bash
cd apps/web
pnpm dev
```

Current web runtime commands:
```bash
cd apps/web
pnpm dev
pnpm typegen
pnpm test
pnpm build
pnpm preview
```

### Mobile Development
```bash
pnpm --filter race-tracker-mobile start
pnpm --filter race-tracker-mobile exec tsc --noEmit
pnpm --filter race-tracker-mobile lint
pnpm --filter race-tracker-mobile exec expo-doctor
```

## Deployment

The portable pilot composition is `compose.pilot.yml`. It runs the backend,
same-origin Nginx web/API proxy, and password-protected Redis; PostgreSQL/PostGIS
can be supplied externally. The optional `local-db` profile is for local migration
and rehearsal use only. Do not treat the example values as deployable credentials.
The web service's published HTTP port is intended to sit behind an HTTPS-terminating
load balancer or reverse proxy in production; do not expose it directly to browsers,
because production refresh cookies are marked `Secure`.

```bash
cp .env.pilot.example .env.pilot
# Replace every CHANGE_ME value using a secret manager before starting.
docker compose --env-file .env.pilot -f compose.pilot.yml up --build
```

Set the external database host/credentials in the protected environment before
launch. For a local disposable PostGIS database, add `--profile local-db`. The
optional RFID gateway is started with `--profile rfid`; keep it in simulation mode
until the separate hardware acceptance gate is recorded.

See [the pilot operations runbook](docs/pilot-operations-runbook.md) for migration,
backup/restore, rollback, incident, and key-rotation procedures. The runbook is a
procedure template until each rehearsal is actually exercised and recorded.

### Mobile App
Build and submit to stores:
```bash
cd apps/mobile
eas build --platform all
eas submit --platform all
```

## Environment Variables

Use the checked-in, credential-free [.env.pilot.example](.env.pilot.example) as
the authoritative pilot variable list. The backend uses `DB_HOST`, `DB_PORT`,
`DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD`; it does not use the old
`DATABASE_URL`/`DATABASE_USERNAME` names shown in historical setup snippets.
Production also requires a non-default `JWT_SECRET`, an explicit HTTPS
`FRONTEND_URL`, secure browser cookies,
and global RFID simulation disabled. Never put real values in this template or
source control.

Mobile build-time API and map settings remain under the `EXPO_PUBLIC_*` prefix;
see `apps/mobile/README.md` and do not place server secrets in client variables.

## License

MIT License - see LICENSE file for details.
