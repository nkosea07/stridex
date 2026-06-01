# Race Tracker Platform - Development Guide

This guide provides detailed instructions and prompts to help you complete the remaining implementation tasks, particularly the Spring Boot backend.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Backend Implementation Tasks](#backend-implementation-tasks)
3. [API Contract Reference](#api-contract-reference)
4. [Database Setup](#database-setup)
5. [Authentication Implementation](#authentication-implementation)
6. [Real-Time Tracking Implementation](#real-time-tracking-implementation)
7. [Integration Points](#integration-points)
8. [Testing Strategy](#testing-strategy)
9. [Deployment Considerations](#deployment-considerations)

---

## Project Overview

### Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Mobile App    │     │     Web App     │     │  Public Website │
│  (React Native) │     │  (React Vite)   │     │   (Optional)    │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         │              ┌────────┴────────┐              │
         └──────────────┤   API Gateway   ├──────────────┘
                        │  (Spring Boot)  │
                        └────────┬────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
     ┌────────┴────────┐ ┌──────┴──────┐ ┌────────┴────────┐
     │   PostgreSQL    │ │    Redis    │ │   WebSocket     │
     │   (Primary DB)  │ │  (Caching)  │ │  (Real-time)    │
     └─────────────────┘ └─────────────┘ └─────────────────┘
```

### Tech Stack Summary

| Layer | Technology | Purpose |
|-------|------------|---------|
| Mobile | React Native + Expo | Athlete app (iOS/Android) |
| Web App | React + Vite + Tailwind | Public spectator and race operations surface |
| Backend | Spring Boot 3.5 | REST API + WebSocket server |
| Database | PostgreSQL 15+ with PostGIS | Primary data store and spatial query support |
| Cache | Redis (optional) | Session/caching layer |
| Auth | JWT + Spring Security | Authentication/Authorization |

### Public Spectator Flow

Phase 1 adds a public spectator web surface without changing the protected admin and athlete flows:
- Public web routes: `/race/:raceId` and `/race/:raceId/athlete/:participantId`
- Public backend endpoints: `GET /api/v1/public/races/{raceId}/hub`, `GET /api/v1/public/races/{raceId}/tracking/live`, `GET /api/v1/public/races/{raceId}/tracking/search?q=<term>`, and `GET /api/v1/public/races/{raceId}/tracking/athletes/{participantId}`
- Security model: anonymous `GET` access is limited to the public endpoint prefix; admin and athlete routes remain authenticated

### Backend-to-Web Public Data Path

The public spectator path is now:
1. Tracking data is recorded through the existing tracking domain and race operations flow.
2. The backend exposes a spectator-safe public read layer under `/api/v1/public/races/...`.
3. The web app consumes those endpoints for the shareable race hub and athlete detail routes.

Keep this phase intentionally narrow. Premium mobile fan experiences and RFID/device ingest remain later-phase work and are out of scope here.

### Web Modernization Direction

The web app is moving toward React Router route-managed data loading so routing, loader lifecycles, and revalidation for the public spectator surface are owned by route modules instead of ad hoc page bootstrapping. This keeps data requirements close to the route boundary and makes the public web surface easier to evolve without rewriting the rest of `apps/web`.

Current route modules live in `apps/web/app/`, with most reusable UI, page composition, API clients, and shared logic still living in `apps/web/src/`.

This foundation does not include deeper prerender or SSR rollout, broad admin/operator data-loading refactors, or non-web modernization work. Those remain deferred to later modernization plans.

### Backend Platform and Observability Direction

The backend platform modernization follows the public web routing work because the public spectator surface depends on reliable backend reads, tracking writes, and live tracking fanout. The current backend baseline is Spring Boot 3.5 with Actuator enabled under the existing `/api` servlet context.

Management endpoint policy:
- Public probes: `GET /api/actuator/health`, `GET /api/actuator/health/liveness`, and `GET /api/actuator/health/readiness`
- Public safe metadata: `GET /api/actuator/info`
- Authenticated metrics scrape: `GET /api/actuator/prometheus`

Micrometer `@Observed` instrumentation exists on the public tracking and tracking ingest hot paths. The stable observation names are `tracking.public.race_hub`, `tracking.public.live_athletes`, `tracking.public.athlete_detail`, `tracking.ingest.location`, `tracking.ingest.location_batch`, `tracking.ingest.checkpoint`, `tracking.live.aggregate`, and `tracking.live.participant_track`.

This phase does not include external OpenTelemetry collector/exporter setup, telemetry partitioning, or RFID/bib device ingest. The PostGIS/geospatial schema foundation is in place and remains deliberately narrow: spatial columns are query fields, while the existing decimal latitude/longitude API fields stay unchanged.

### Mobile Runtime and Offline Tracking Direction

The mobile app now targets Expo SDK 54 with React Native 0.81 and React 19.1. This is the bridge runtime before a later Expo SDK 55 / mandatory New Architecture migration.

Mobile runtime policy:
- Node baseline is `>=20.19`
- package management is pnpm only
- `pnpm --filter race-tracker-mobile exec expo-doctor` is part of the verification loop
- native behavior should be tested with a development build, not only Expo Go

Offline tracking model:
1. Expo Location remains the primary live tracking mechanism.
2. GPS points are queued in SQLite with sync status, retry counts, last error, and idempotency metadata.
3. Failed sync attempts keep points queued for retry; they are not marked synced after a network failure.
4. Active tracking sessions are persisted locally and recovered during app startup before the splash screen is hidden.
5. `expo-background-task` is an opportunistic catch-up worker for queued points, not immediate race timing infrastructure.

EAS Update readiness:
- `apps/mobile/eas.json` defines `development`, `preview`, and `production` channels.
- `app.json` uses an app-version runtime policy for OTA compatibility.
- No fake Expo project id or update URL is checked in. Add account-backed EAS values only when publishing real updates.

Deferred mobile work:
- Expo SDK 55 / New Architecture-only migration
- backend push-token registration and notification preferences
- premium mobile fan features
- mobile crash/error observability
- RFID/bib ingest integration

---

## Backend Implementation Tasks

### Task 1: Project Setup and Configuration

**Prompt for AI Assistant (Cursor/GitHub Copilot):**

```
I need to set up a Spring Boot 3.x project for a Race/Marathon Tracking System.

Requirements:
1. Use Java 17+
2. Include these dependencies:
   - Spring Web
   - Spring Data JPA
   - Spring Security
   - Spring WebSocket
   - PostgreSQL Driver
   - Lombok
   - JWT (jjwt library)
   - MapStruct for DTOs
   - Validation (jakarta.validation)
   - OpenAPI/Swagger documentation

3. Configure application.yml for:
   - PostgreSQL connection (use environment variables)
   - JWT secret and expiration settings
   - CORS configuration for localhost:5173 (web) and mobile origins
   - WebSocket endpoints

4. Set up the package structure:
   com.racetracker/
   ├── config/          # Security, WebSocket, CORS configs
   ├── controller/      # REST controllers
   ├── dto/             # Data Transfer Objects
   ├── exception/       # Custom exceptions and handlers
   ├── mapper/          # MapStruct mappers
   ├── model/           # JPA entities (already created)
   ├── repository/      # Spring Data repositories
   ├── security/        # JWT filter, user details service
   ├── service/         # Business logic
   └── websocket/       # WebSocket handlers

Generate the main application class and all configuration classes.
```

### Task 2: Repository Layer

**Prompt:**

```
Create Spring Data JPA repositories for my Race Tracker entities.

Entities (already exist in com.racetracker.model):
- User (id, email, passwordHash, firstName, lastName, phone, role, createdAt)
- Race (id, name, description, raceDate, startTime, status, location, etc.)
- RaceCategory (id, race, name, distance, maxParticipants, etc.)
- Participant (id, user, race, category, bibNumber, status, registeredAt)
- Checkpoint (id, race, name, distanceFromStart, latitude, longitude, sequence)
- CheckpointRecord (id, participant, checkpoint, timestamp, recordedBy, method)
- GpsLocation (id, participant, race, latitude, longitude, altitude, timestamp)

For each repository, include:
1. Basic CRUD operations (extend JpaRepository)
2. Custom query methods needed for the application

Example custom queries needed:
- UserRepository: findByEmail, existsByEmail
- RaceRepository: findByStatus, findUpcomingRaces, findByDateRange
- ParticipantRepository: findByRaceId, findByUserId, findByBibNumber, countByRaceId
- CheckpointRepository: findByRaceIdOrderBySequence
- CheckpointRecordRepository: findByParticipantId, findByCheckpointId, getLatestForParticipant
- GpsLocationRepository: findByParticipantIdAndRaceId, findLatestByParticipantId, findByRaceIdAndTimestampAfter

Use @Query annotations with JPQL for complex queries.
Include pagination support where appropriate (Pageable parameter).
```

### Task 3: DTO and Mapper Layer

**Prompt:**

```
Create DTOs and MapStruct mappers for my Race Tracker API.

I need request/response DTOs for:

1. Authentication:
   - LoginRequest (email, password)
   - RegisterRequest (email, password, firstName, lastName, phone)
   - AuthResponse (token, refreshToken, user details)
   - UserDTO (id, email, firstName, lastName, phone, role)

2. Race Management:
   - RaceDTO (full race details)
   - RaceCreateRequest (name, description, date, location, categories)
   - RaceUpdateRequest (same as create, for updates)
   - RaceListResponse (paginated list with summary info)
   - RaceSummaryDTO (id, name, date, status, participantCount)

3. Participant:
   - ParticipantDTO (full participant details with user info)
   - ParticipantRegistrationRequest (userId, raceId, categoryId, emergencyContact)
   - ParticipantUpdateRequest (status, bibNumber)
   - ParticipantListResponse (paginated)

4. Checkpoint:
   - CheckpointDTO (full checkpoint details)
   - CheckpointCreateRequest (name, distance, lat, lng, sequence)
   - CheckpointRecordDTO (checkpoint crossing record)
   - CheckpointRecordRequest (participantId, checkpointId, timestamp, method)

5. GPS Tracking:
   - GpsLocationDTO (lat, lng, altitude, speed, timestamp)
   - GpsLocationBatchRequest (array of locations for batch upload)
   - LiveTrackingResponse (participant location + stats)

6. Results:
   - RaceResultDTO (participant, finish time, pace, rank, splits)
   - LeaderboardResponse (paginated results with filtering)

Use MapStruct for entity-DTO mapping. Include:
- @Mapper annotations with componentModel = "spring"
- Nested object mapping
- Custom mapping methods where needed
- Null handling strategies
```

### Task 4: Service Layer

**Prompt:**

```
Create service classes for my Race Tracker application.

Services needed:

1. AuthService:
   - register(RegisterRequest): Create new user, hash password, return tokens
   - login(LoginRequest): Validate credentials, generate JWT tokens
   - refreshToken(String refreshToken): Issue new access token
   - getCurrentUser(): Get authenticated user from security context
   - updatePassword(userId, oldPassword, newPassword)

2. UserService:
   - getUserById(Long id)
   - getUserByEmail(String email)
   - updateUser(Long id, UserUpdateRequest)
   - getAllUsers(Pageable, filters) - for admin
   - updateUserRole(Long id, Role role) - super admin only

3. RaceService:
   - createRace(RaceCreateRequest): Create race with categories
   - updateRace(Long id, RaceUpdateRequest)
   - getRaceById(Long id)
   - getAllRaces(Pageable, filters)
   - updateRaceStatus(Long id, RaceStatus)
   - startRace(Long id): Set status to IN_PROGRESS, record start time
   - endRace(Long id): Set status to COMPLETED, calculate results
   - deleteRace(Long id): Soft delete

4. ParticipantService:
   - registerParticipant(ParticipantRegistrationRequest)
   - updateParticipant(Long id, ParticipantUpdateRequest)
   - getParticipantById(Long id)
   - getParticipantsByRace(Long raceId, Pageable)
   - getParticipantsByUser(Long userId)
   - assignBibNumber(Long participantId, String bibNumber)
   - bulkAssignBibNumbers(Long raceId): Auto-assign sequential bibs
   - updateParticipantStatus(Long id, ParticipantStatus)
   - checkInParticipant(Long id): Mark as checked in on race day

5. CheckpointService:
   - createCheckpoint(CheckpointCreateRequest)
   - updateCheckpoint(Long id, CheckpointUpdateRequest)
   - getCheckpointsByRace(Long raceId)
   - deleteCheckpoint(Long id)
   - recordCheckpointCrossing(CheckpointRecordRequest)
   - getCheckpointRecordsForParticipant(Long participantId)
   - getCheckpointRecordsForCheckpoint(Long checkpointId, Pageable)

6. TrackingService:
   - recordGpsLocation(Long participantId, GpsLocationDTO)
   - recordGpsLocationBatch(Long participantId, List<GpsLocationDTO>)
   - getLatestLocations(Long raceId): Get latest position of all active participants
   - getParticipantTrack(Long participantId, Long raceId): Full GPS track
   - calculateStats(Long participantId): Current pace, distance, estimated finish

7. ResultService:
   - calculateResults(Long raceId): Process all finishers, calculate times/ranks
   - getLeaderboard(Long raceId, filters, Pageable)
   - getParticipantResult(Long participantId)
   - exportResults(Long raceId, format): CSV/PDF export
   - publishResults(Long raceId): Make results public

Include:
- @Service and @Transactional annotations
- Proper exception handling (throw custom exceptions)
- Validation logic
- Logging with SLF4J
- Role-based access checks where appropriate
```

### Task 5: Security Configuration

**Prompt:**

```
Implement Spring Security with JWT authentication for my Race Tracker API.

Requirements:

1. JwtTokenProvider class:
   - generateAccessToken(User user): Create JWT with user id, email, role
   - generateRefreshToken(User user): Longer-lived token for refresh
   - validateToken(String token): Verify signature and expiration
   - getUserIdFromToken(String token): Extract user ID claim
   - Access token expiry: 15 minutes
   - Refresh token expiry: 7 days

2. JwtAuthenticationFilter (extends OncePerRequestFilter):
   - Extract token from Authorization header (Bearer scheme)
   - Validate token
   - Load user details and set authentication in SecurityContext
   - Handle token errors gracefully

3. CustomUserDetailsService (implements UserDetailsService):
   - Load user by email
   - Return UserDetails with granted authorities based on role

4. SecurityConfig class:
   - Configure HttpSecurity with these rules:
     - /api/auth/** - permitAll (login, register)
     - /api/public/** - permitAll (public race info, results)
     - /api/admin/** - hasRole('SUPER_ADMIN')
     - /api/races/*/manage/** - hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')
     - /api/checkpoints/record - hasAnyRole('RACE_ADMIN', 'VOLUNTEER')
     - /api/** - authenticated
     - /ws-tracking/** - permitAll (WebSocket, auth handled differently)
   - Disable CSRF (stateless API)
   - Configure CORS
   - Add JWT filter before UsernamePasswordAuthenticationFilter
   - Configure exception handling for auth errors

5. Role enum with authorities:
   - SUPER_ADMIN: All permissions
   - RACE_ADMIN: Manage assigned races
   - VOLUNTEER: Record checkpoints only
   - ATHLETE: View own data, participate in races

6. Method-level security:
   - Enable @PreAuthorize annotations
   - Create custom security expressions if needed
```

### Task 6: REST Controllers

**Prompt:**

```
Create REST controllers for my Race Tracker API.

Follow these conventions:
- Base path: /api/v1
- Use ResponseEntity for all responses
- Include OpenAPI/Swagger annotations
- Validate request bodies with @Valid
- Use appropriate HTTP methods and status codes

Controllers needed:

1. AuthController (/api/v1/auth):
   POST /register - Register new user
   POST /login - Login, return tokens
   POST /refresh - Refresh access token
   GET /me - Get current user
   PUT /password - Change password

2. UserController (/api/v1/users):
   GET / - List users (admin only, paginated)
   GET /{id} - Get user by ID
   PUT /{id} - Update user
   PUT /{id}/role - Update user role (super admin)
   DELETE /{id} - Deactivate user (super admin)

3. RaceController (/api/v1/races):
   GET / - List races (public, with filters)
   GET /{id} - Get race details
   POST / - Create race (admin)
   PUT /{id} - Update race (admin)
   DELETE /{id} - Delete race (admin)
   POST /{id}/start - Start race (admin)
   POST /{id}/end - End race (admin)
   GET /{id}/categories - Get race categories
   POST /{id}/categories - Add category (admin)

4. ParticipantController (/api/v1/participants):
   GET /race/{raceId} - List participants for race
   GET /user/{userId} - List user's registrations
   GET /{id} - Get participant details
   POST / - Register for race
   PUT /{id} - Update participant (admin)
   PUT /{id}/status - Update status (admin)
   PUT /{id}/bib - Assign bib number (admin)
   POST /race/{raceId}/assign-bibs - Bulk assign bibs (admin)
   POST /{id}/checkin - Check in participant (admin/volunteer)

5. CheckpointController (/api/v1/checkpoints):
   GET /race/{raceId} - List checkpoints for race
   GET /{id} - Get checkpoint details
   POST / - Create checkpoint (admin)
   PUT /{id} - Update checkpoint (admin)
   DELETE /{id} - Delete checkpoint (admin)
   POST /record - Record checkpoint crossing (admin/volunteer)
   GET /records/participant/{participantId} - Get participant's records
   GET /records/checkpoint/{checkpointId} - Get checkpoint records

6. TrackingController (/api/v1/tracking):
   POST /location - Record single GPS location (athlete)
   POST /location/batch - Record batch of locations (athlete)
   GET /live/{raceId} - Get live positions for race
   GET /track/{participantId}/{raceId} - Get GPS track

7. ResultController (/api/v1/results):
   GET /race/{raceId} - Get race leaderboard (public)
   GET /participant/{participantId} - Get participant result
   POST /race/{raceId}/calculate - Calculate results (admin)
   POST /race/{raceId}/publish - Publish results (admin)
   GET /race/{raceId}/export - Export results CSV

Include proper error handling and consistent response format:
{
  "success": boolean,
  "data": object | array | null,
  "message": string | null,
  "errors": array | null,
  "pagination": { page, size, totalElements, totalPages } | null
}
```

### Task 7: WebSocket Implementation

**Prompt:**

```
Implement WebSocket support for real-time GPS tracking in my Race Tracker.

Requirements:

1. WebSocketConfig:
   - Enable STOMP over WebSocket
   - Configure message broker with /topic prefix
   - Application destination prefix: /app
   - WebSocket endpoint: /ws-tracking
   - Configure CORS for web and mobile clients
   - Add JWT authentication interceptor

2. WebSocket Authentication:
   - Create ChannelInterceptor to validate JWT on CONNECT
   - Extract user from token and set principal
   - Reject connections with invalid/missing tokens

3. TrackingWebSocketController:
   Topics to broadcast:
   - /topic/race/{raceId}/locations - All participant locations for a race
   - /topic/participant/{participantId}/location - Single participant location
   - /topic/race/{raceId}/checkpoint - Checkpoint crossing events
   - /topic/race/{raceId}/updates - Race status changes

   Message endpoints:
   - /app/tracking/location - Receive GPS update from mobile app
   
4. Message DTOs:
   - LocationUpdateMessage (participantId, lat, lng, altitude, speed, timestamp)
   - CheckpointEventMessage (participantId, checkpointId, timestamp, split)
   - RaceStatusMessage (raceId, status, message)

5. Integration with TrackingService:
   - When GPS location is recorded via REST or WebSocket, broadcast to subscribers
   - When checkpoint is recorded, broadcast event
   - When race status changes, broadcast update

6. Error handling:
   - Handle disconnections gracefully
   - Implement reconnection support hints in protocol
   - Log connection/disconnection events

Example message flow:
1. Mobile app connects to /ws with JWT in headers
2. Mobile app subscribes to /topic/race/{raceId}/locations
3. Mobile app sends location to /app/tracking/location
4. Server saves location, broadcasts to /topic/race/{raceId}/locations
5. Web admin receives location update, updates map
```

### Task 8: Exception Handling

**Prompt:**

```
Create a global exception handling system for my Race Tracker API.

Requirements:

1. Custom Exceptions:
   - ResourceNotFoundException (entity not found)
   - DuplicateResourceException (e.g., email already exists)
   - InvalidOperationException (e.g., can't register for completed race)
   - UnauthorizedException (authentication failed)
   - ForbiddenException (no permission)
   - ValidationException (business rule violation)
   - RaceNotActiveException (race not in correct status)
   - ParticipantLimitExceededException
   - InvalidBibNumberException

2. GlobalExceptionHandler (@ControllerAdvice):
   Handle all custom exceptions and return consistent error response:
   {
     "success": false,
     "error": {
       "code": "RESOURCE_NOT_FOUND",
       "message": "Race not found with id: 123",
       "field": null | "fieldName",
       "timestamp": "2024-01-15T10:30:00Z"
     }
   }

3. Handle standard exceptions:
   - MethodArgumentNotValidException (validation errors) -> 400
   - ConstraintViolationException (JPA validation) -> 400
   - DataIntegrityViolationException (DB constraints) -> 409
   - AccessDeniedException -> 403
   - AuthenticationException -> 401
   - HttpMessageNotReadableException (malformed JSON) -> 400
   - Exception (fallback) -> 500

4. For validation errors, return field-level details:
   {
     "success": false,
     "errors": [
       { "field": "email", "message": "must be a valid email address" },
       { "field": "password", "message": "must be at least 8 characters" }
     ]
   }

5. Log all 500 errors with full stack trace
6. Don't expose internal details in production (use generic message)
```

---

## API Contract Reference

### Authentication Endpoints

```yaml
POST /api/v1/auth/register:
  request:
    email: string (required, valid email)
    password: string (required, min 8 chars)
    firstName: string (required)
    lastName: string (required)
    phone: string (optional)
  response:
    token: string (JWT access token)
    refreshToken: string
    user: UserDTO

POST /api/v1/auth/login:
  request:
    email: string
    password: string
  response:
    token: string
    refreshToken: string
    user: UserDTO
```

### Race Endpoints

```yaml
GET /api/v1/races:
  params:
    status: string (DRAFT, UPCOMING, IN_PROGRESS, COMPLETED)
    fromDate: date
    toDate: date
    search: string
    page: int
    size: int
    sort: string
  response:
    data: RaceDTO[]
    pagination: PaginationInfo

POST /api/v1/races:
  headers:
    Authorization: Bearer {token}
  request:
    name: string (required)
    description: string
    raceDate: date (required)
    startTime: time
    registrationDeadline: datetime
    location: string
    latitude: number
    longitude: number
    maxParticipants: int
    categories: CategoryDTO[]
  response:
    data: RaceDTO
```

### GPS Tracking Endpoints

```yaml
POST /api/v1/tracking/location:
  headers:
    Authorization: Bearer {token}
  request:
    participantId: long
    raceId: long
    latitude: number (required, -90 to 90)
    longitude: number (required, -180 to 180)
    altitude: number (optional)
    speed: number (optional, m/s)
    accuracy: number (optional, meters)
    timestamp: datetime (required, ISO 8601)
  response:
    success: true
    data:
      distance: number (meters from start)
      pace: string (min/km)
      estimatedFinish: datetime

GET /api/v1/tracking/live/{raceId}:
  response:
    data:
      - participantId: long
        bibNumber: string
        name: string
        latitude: number
        longitude: number
        lastUpdate: datetime
        distance: number
        pace: string
        status: string
```

---

## Database Setup

### Geospatial Foundation

The backend schema now enables PostGIS and adds `geography(Point, 4326)` query columns for race start/finish locations, checkpoints, and GPS locations. Existing decimal latitude/longitude columns remain the compatibility fields for DTOs, JPA entities, and current API payloads. Database triggers keep the spatial query columns synchronized when coordinates are inserted or updated.

Use the spatial columns through repository/service boundaries instead of spreading raw spatial SQL across controllers. `ST_DWithin` plus GiST indexes is the intended path for proximity checks such as checkpoint geofencing.

For existing deployments, apply `apps/backend/database/migrations/2026-06-01-geospatial-telemetry-foundation.sql`. It enables PostGIS, adds/backfills the geography columns, creates sync triggers, and adds the GiST indexes without changing API contracts.

Telemetry table growth remains a production risk. Convert `gps_locations` to range partitioning on `recorded_at` in a dedicated migration when race-day volume justifies it; do not rewrite the table inside the current schema foundation. RFID/bib ingest should stay deferred until this location storage and proximity-query foundation is stable.

### Initial Setup Commands

```bash
# Start PostgreSQL with Docker
cd apps/backend
docker-compose up -d

# Or connect to existing PostgreSQL
export DATABASE_URL=postgresql://user:password@localhost:5432/racetracker

# Run schema (using psql or your preferred tool)
psql $DATABASE_URL -f database/schema.sql
```

The target database must support PostGIS before the schema is applied. Managed PostgreSQL providers usually require enabling the extension at the database level first.

### Sample Data Script

Create `apps/backend/database/seed.sql`:

```sql
-- Insert sample users
INSERT INTO users (email, password_hash, first_name, last_name, role, created_at) VALUES
('admin@racetracker.com', '$2a$10$...', 'Admin', 'User', 'SUPER_ADMIN', NOW()),
('race.manager@racetracker.com', '$2a$10$...', 'Race', 'Manager', 'RACE_ADMIN', NOW()),
('volunteer@racetracker.com', '$2a$10$...', 'Volunteer', 'Helper', 'VOLUNTEER', NOW()),
('athlete@example.com', '$2a$10$...', 'John', 'Runner', 'ATHLETE', NOW());

-- Insert sample race
INSERT INTO races (name, description, race_date, start_time, status, location, latitude, longitude, created_by, created_at) VALUES
('Cape Town Marathon 2024', 'Annual marathon through the beautiful streets of Cape Town', '2024-10-15', '06:00:00', 'UPCOMING', 'Cape Town, South Africa', -33.9249, 18.4241, 1, NOW());

-- Insert categories for the race
INSERT INTO race_categories (race_id, name, distance_km, max_participants, entry_fee) VALUES
(1, 'Full Marathon', 42.195, 5000, 500.00),
(1, 'Half Marathon', 21.0975, 8000, 350.00),
(1, '10K Fun Run', 10.0, 10000, 200.00);

-- Insert checkpoints
INSERT INTO checkpoints (race_id, name, distance_from_start, latitude, longitude, sequence, has_timing_mat, has_water_station) VALUES
(1, 'Start Line', 0, -33.9249, 18.4241, 1, true, true),
(1, '5K Mark', 5000, -33.9150, 18.4300, 2, true, true),
(1, '10K Mark', 10000, -33.9050, 18.4400, 3, true, true),
(1, 'Half Marathon', 21097, -33.8900, 18.4500, 4, true, true),
(1, '30K Mark', 30000, -33.8800, 18.4600, 5, true, true),
(1, 'Finish Line', 42195, -33.9249, 18.4241, 6, true, true);
```

---

## Authentication Implementation

### Password Hashing

Use BCrypt with strength 10:

```java
@Service
public class AuthService {
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
    
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered");
        }
        
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(Role.ATHLETE); // Default role
        
        return userRepository.save(user);
    }
}
```

### JWT Token Structure

```json
{
  "sub": "user@example.com",
  "userId": 123,
  "role": "RACE_ADMIN",
  "iat": 1705312800,
  "exp": 1705313700
}
```

---

## Real-Time Tracking Implementation

### Mobile App GPS Collection Strategy

1. **Background Location Service**:
   - Request `ACCESS_FINE_LOCATION` and `ACCESS_BACKGROUND_LOCATION`
   - Use Expo Location with background task
   - Collect every 5-10 seconds during active race
   - Batch upload every 30 seconds or on significant location change

2. **Battery Optimization**:
   - Use `ACTIVITY_RECOGNITION` to detect running vs walking
   - Reduce frequency when stationary
   - Respect battery saver mode

3. **Offline Support**:
   - Queue locations locally if network unavailable
   - Sync when connection restored
   - Include sequence numbers for ordering

### WebSocket Message Protocol

```typescript
// Client -> Server: Location Update
{
  "type": "LOCATION_UPDATE",
  "payload": {
    "participantId": 123,
    "raceId": 1,
    "locations": [
      {
        "latitude": -33.9249,
        "longitude": 18.4241,
        "altitude": 15.5,
        "speed": 3.2,
        "accuracy": 5,
        "timestamp": "2024-01-15T06:15:30Z"
      }
    ]
  }
}

// Server -> Client: Broadcast to subscribers
{
  "type": "PARTICIPANT_LOCATION",
  "payload": {
    "participantId": 123,
    "bibNumber": "A1234",
    "name": "John Runner",
    "latitude": -33.9249,
    "longitude": 18.4241,
    "distance": 5234,
    "pace": "5:30",
    "rank": 45,
    "lastCheckpoint": "5K Mark",
    "timestamp": "2024-01-15T06:15:30Z"
  }
}
```

---

## Integration Points

### Web App -> Backend

The web app is pre-configured to call `VITE_API_URL` (default: `http://localhost:8080/api/v1`).

Update `apps/web/.env`:
```
VITE_API_URL=http://localhost:8080/api/v1
VITE_WS_URL=ws://localhost:8080/ws
VITE_MAPBOX_TOKEN=your_mapbox_token
```

### Mobile App -> Backend

Update `apps/mobile/lib/api.ts`:
```typescript
const API_BASE_URL = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8080/api/v1';
const WS_URL = process.env.EXPO_PUBLIC_WS_URL || 'ws://localhost:8080/ws';
```

### CORS Configuration

Ensure Spring Boot allows these origins:
- `http://localhost:5173` (web dev)
- `http://localhost:19006` (Expo web)
- `exp://` (Expo Go)
- Production domains

---

## Testing Strategy

### Unit Tests (JUnit 5 + Mockito)

```java
@ExtendWith(MockitoExtension.class)
class RaceServiceTest {
    @Mock
    private RaceRepository raceRepository;
    
    @InjectMocks
    private RaceService raceService;
    
    @Test
    void shouldCreateRace() {
        // Given
        RaceCreateRequest request = new RaceCreateRequest();
        request.setName("Test Marathon");
        
        // When
        when(raceRepository.save(any())).thenReturn(new Race());
        Race result = raceService.createRace(request);
        
        // Then
        assertNotNull(result);
        verify(raceRepository).save(any());
    }
}
```

### Integration Tests (Spring Boot Test)

```java
@SpringBootTest
@AutoConfigureMockMvc
class RaceControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @WithMockUser(roles = "RACE_ADMIN")
    void shouldCreateRace() throws Exception {
        String json = """
            {
              "name": "Test Marathon",
              "raceDate": "2024-10-15",
              "location": "Cape Town"
            }
            """;
        
        mockMvc.perform(post("/api/v1/races")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.name").value("Test Marathon"));
    }
}
```

---

## Deployment Considerations

### Environment Variables

```bash
# Database
DATABASE_URL=postgresql://user:pass@host:5432/racetracker
DATABASE_POOL_SIZE=20

# JWT
JWT_SECRET='7uZwPYOey3oFwOojL/qWbYeq/3Scq6RMIEtXZzIZO5Uygj6toRS29z8o6pQslC18ZfaibKtKhCD2Q/agm0YmCQ=='
JWT_ACCESS_EXPIRY=900000      # 15 minutes
JWT_REFRESH_EXPIRY=604800000  # 7 days

# Server
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=https://admin.yourrace.com,https://yourrace.com

# Redis (optional)
REDIS_URL=redis://localhost:6379

# Monitoring
SENTRY_DSN=your-sentry-dsn
```

### Production Checklist

- [ ] Set strong JWT secret (min 256 bits)
- [ ] Configure HTTPS/TLS
- [ ] Confirm PostGIS is available before applying production schema changes
- [ ] Set up database connection pooling
- [ ] Configure rate limiting
- [ ] Set up health check endpoint (/actuator/health)
- [ ] Configure logging to external service
- [ ] Set up database backups
- [ ] Configure WebSocket scaling (if using multiple instances)
- [ ] Set up monitoring (Prometheus/Grafana or similar)

---

## Quick Start Commands

```bash
# 1. Start database
cd apps/backend
docker-compose up -d

# 2. Run backend (after implementing)
mvn spring-boot:run

# 3. Run web app
cd apps/web
pnpm install
pnpm dev

# 4. Run mobile app
pnpm --filter race-tracker-mobile start
```

---

## Additional Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Security JWT Guide](https://www.baeldung.com/spring-security-oauth-jwt)
- [Spring WebSocket Guide](https://spring.io/guides/gs/messaging-stomp-websocket/)
- [Mapbox GL JS](https://docs.mapbox.com/mapbox-gl-js/)
- [Expo Location](https://docs.expo.dev/versions/latest/sdk/location/)
- [React Native Maps](https://github.com/react-native-maps/react-native-maps)

---

## Support

If you need help implementing any specific part, use this prompt format with your AI assistant:

```
Context: I'm building a Race/Marathon Tracking System with Spring Boot backend.
Current state: [describe what you have]
Problem: [describe the specific issue or feature]
Relevant code: [paste relevant snippets]
Expected behavior: [what should happen]
```

Good luck with your implementation!
