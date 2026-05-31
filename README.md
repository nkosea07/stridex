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
- Spring Boot 3.2
- Spring Security + JWT
- Spring Data JPA
- PostgreSQL 15
- WebSocket (STOMP)

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
- React Native (Expo)
- Expo Router
- Zustand
- React Native Maps
- Expo Location & Camera

[View Mobile README](apps/mobile/README.md)

## Product Surfaces

- `apps/web`: serves both public spectators and authenticated/admin workflows. Public Phase 1 routes include `/race/:raceId` and `/race/:raceId/athlete/:participantId`.
- `apps/mobile`: remains the richer logged-in experience for athletes. Premium fan-oriented mobile features are later phases and out of scope for this foundation.
- `apps/backend`: serves protected admin/athlete APIs plus anonymous public spectator read endpoints.

## Getting Started

### Prerequisites
- Node.js 18+
- Java 21
- PostgreSQL 15
- pnpm (recommended) or npm

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
cd apps/mobile
npx expo start
```

## Features

### Live GPS Tracking
- Real-time athlete location tracking
- Route visualization on maps
- Pace and distance calculations
- Estimated finish time predictions

### Multiple Tracking Methods
- GPS tracking from mobile app
- RFID/chip timing at checkpoints
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
- `gps_locations` - GPS tracking data

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
cd apps/mobile
npx expo start
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
