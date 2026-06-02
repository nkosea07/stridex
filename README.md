# Race Tracker Platform

A comprehensive race/marathon tracking system with live GPS tracking, checkpoint management, and real-time results.

## Architecture

This is a monorepo containing three applications:

```
├── apps/
│   ├── backend/       # Java Spring Boot API (PostgreSQL)
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
- External OpenTelemetry exporter setup, telemetry partitioning, and RFID/bib ingest are later modernization phases.

Mobile modernization status:
- `apps/mobile` targets Expo SDK 54 as the bridge runtime before any SDK 55/New Architecture-only migration.
- Active tracking sessions and queued GPS points are persisted in SQLite so normal app lifecycle interruptions do not discard race context.
- `expo-background-task` is configured only for opportunistic catch-up sync; live race timing still depends on Expo Location foreground/background updates.
- EAS build channels and `expo-updates` runtime-version policy are scaffolded, but a real Expo project id/update URL must be added when EAS Update is activated for an account.
- Backend push-token registration, premium mobile fan features, SDK 55 migration, and RFID/bib ingest remain later phases.

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
- Pace and distance calculations
- Estimated finish time predictions

### Multiple Tracking Methods
- GPS tracking from mobile app
- RFID/chip timing bridge ingest at checkpoints
- QR code scanning at checkpoints
- Manual checkpoint entry

### User Roles
- **Super Admin**: Full platform access
- **Race Admin**: Manage specific races
- **Volunteer**: Checkpoint operations

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

PostGIS is enabled by the schema for spatial query fields. Keep the decimal latitude/longitude fields for API compatibility; database triggers maintain the geography columns, and GiST indexes support proximity queries such as checkpoint geofencing. Existing deployments should apply `apps/backend/database/migrations/2026-06-01-geospatial-telemetry-foundation.sql`. High-volume `gps_locations` partitioning by `recorded_at` is planned as a future migration, not part of the current schema foundation.

RFID bridge ingestion is backend-first: operators register checkpoint readers, receive a one-time API key, and timing bridges submit batched reads to `/api/v1/rfid/read-events`. The backend stores raw read outcomes and converts valid first-time reads into normal checkpoint records. Operator monitoring UI, CSV timing imports, and vendor-specific adapters remain later phases.

See `apps/backend/database/schema.sql` for full schema.

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
- `WebSocket /ws-tracking` - Real-time updates

### Results
- `GET /api/v1/races/:id/results` - Race results
- `GET /api/v1/results/my` - User's results

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

### Backend
Deploy Spring Boot to any Java-compatible hosting:
- AWS Elastic Beanstalk
- Google Cloud Run
- Azure App Service
- DigitalOcean App Platform

### Web Application
Deploy Vite app to:
- Vercel
- Netlify
- AWS Amplify

### Mobile App
Build and submit to stores:
```bash
cd apps/mobile
eas build --platform all
eas submit --platform all
```

## Environment Variables

### Backend
```env
DATABASE_URL=jdbc:postgresql://localhost:5432/racetracker
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=password
JWT_SECRET=your-secret-key
```

### Web
```env
VITE_API_URL=http://localhost:8080/api
VITE_WS_URL=ws://localhost:8080/ws
VITE_MAPBOX_TOKEN=your-mapbox-token
```

### Mobile
```env
EXPO_PUBLIC_API_URL=http://localhost:8080/api
EXPO_PUBLIC_MAPBOX_TOKEN=your-mapbox-token
```

## License

MIT License - see LICENSE file for details.
