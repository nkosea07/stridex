# Race Tracker Backend

Spring Boot API for race administration, participant tracking, results, and notifications.

## Stack
- Java 17
- Spring Boot 3.5
- Spring Security (JWT)
- Spring Data JPA (PostgreSQL)
- PostGIS for spatial query fields and proximity indexes
- Spring WebSocket
- Spring Boot Actuator
- Micrometer Observation + Prometheus metrics
- MapStruct
- springdoc OpenAPI + Swagger UI

## Run
```bash
cd apps/backend
mvn spring-boot:run
```

The backend runs under the `/api` servlet context path, so versioned controller routes are exposed under `/api/v1`.

Management endpoints also use the same context path and are exposed under `/api/actuator`.

## Configuration
Main config file:
- `src/main/resources/application.yml`

Required environment variables:
- `JWT_SECRET`

Common database variables:
- `DB_HOST` (default `localhost`)
- `DB_PORT` (default `5432`)
- `DB_NAME` (default `race_tracker`)
- `DB_USERNAME` (default `race_admin`)
- `DB_PASSWORD` (default `password`)

Database requirements:
- PostgreSQL must have PostGIS available before applying `database/schema.sql`; the schema enables it with `CREATE EXTENSION IF NOT EXISTS postgis`.
- Decimal latitude/longitude columns remain the API compatibility fields.
- `geography(Point, 4326)` columns on races, checkpoints, and GPS locations are query fields for spatial operations.
- GiST indexes support the intended `ST_DWithin` proximity path for checkpoint/race-day location checks.
- `gps_locations` partitioning is intentionally deferred to a dedicated migration on `recorded_at` so existing JPA/API behavior stays stable.

## Management and Observability

Actuator is configured as the backend operational surface:
- `GET /api/actuator/health` is public for load balancers and uptime checks.
- `GET /api/actuator/health/liveness` is public for liveness probes.
- `GET /api/actuator/health/readiness` is public for readiness probes.
- `GET /api/actuator/info` is public, but limited to explicit safe app metadata and optional build metadata.
- `GET /api/actuator/prometheus` is exposed for metrics scraping but requires authentication.

The public tracking and tracking ingest hot paths are instrumented with Micrometer `@Observed` names:
- `tracking.public.race_hub`
- `tracking.public.live_athletes`
- `tracking.public.athlete_detail`
- `tracking.ingest.location`
- `tracking.ingest.location_batch`
- `tracking.ingest.checkpoint`
- `tracking.live.aggregate`
- `tracking.live.participant_track`

This phase does not configure an external OpenTelemetry collector/exporter. The geospatial schema foundation is present; telemetry partitioning and RFID/bib device ingest remain later modernization phases.

## API Surface (Implemented)

### Auth
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/auth/me`
- `PUT /api/v1/auth/profile`

### Races
- `GET /api/v1/races`
- `GET /api/v1/races/{id}`
- `POST /api/v1/races`
- `PUT /api/v1/races/{id}`
- `PATCH /api/v1/races/{id}/status`
- `DELETE /api/v1/races/{id}`

### Participants and Registration
- `GET /api/v1/races/{raceId}/participants`
- `GET /api/v1/races/{raceId}/participants/{id}`
- `POST /api/v1/races/{raceId}/participants`
- `PUT /api/v1/races/{raceId}/participants/{id}`
- `DELETE /api/v1/races/{raceId}/participants/{id}`
- `POST /api/v1/races/{raceId}/participants/{id}/check-in`
- `POST /api/v1/races/{raceId}/participants/import`
- `GET /api/v1/races/{raceId}/participants/export`
- `POST /api/v1/races/{raceId}/register`
- `GET /api/v1/participants/my-registrations`
- `DELETE /api/v1/registrations/{registrationId}`

### Checkpoints
- `GET /api/v1/races/{raceId}/checkpoints`
- `POST /api/v1/races/{raceId}/checkpoints`
- `PUT /api/v1/races/{raceId}/checkpoints/{checkpointId}`
- `DELETE /api/v1/races/{raceId}/checkpoints/{checkpointId}`
- `POST /api/v1/races/{raceId}/checkpoints/{checkpointId}/record`

### Tracking
- `POST /api/v1/tracking/{raceId}/location`
- `POST /api/v1/tracking/{raceId}/locations`
- `POST /api/v1/tracking/{raceId}/checkpoint`
- `GET /api/v1/races/{raceId}/tracking/live`
- `GET /api/v1/races/{raceId}/tracking/{participantId}`

### Public Spectator Tracking
- `GET /api/v1/public/races/{raceId}/hub`
- `GET /api/v1/public/races/{raceId}/tracking/live`
- `GET /api/v1/public/races/{raceId}/tracking/search?q=<term>`
- `GET /api/v1/public/races/{raceId}/tracking/athletes/{participantId}`

Anonymous access is expected for `GET` requests under `/api/v1/public/races/**` only. Existing admin, organizer, and athlete routes remain protected by the normal security rules.

Public payloads must stay spectator-safe. Include only race summary, leaderboard, split, and athlete tracking fields needed for public viewing, and do not return email addresses, phone numbers, emergency contacts, medical notes, device tokens, internal notes, or other admin-only audit data.

### Results
- `GET /api/v1/races/{raceId}/results`
- `GET /api/v1/races/{raceId}/results/{participantId}`
- `GET /api/v1/results/my`
- `POST /api/v1/races/{raceId}/results/publish`

### Dashboard and Notifications
- `GET /api/v1/dashboard/stats`
- `GET /api/v1/notifications`
- `PUT /api/v1/notifications/{id}/read`

## WebSocket (Live Tracking)
Endpoint:
- `ws://<host>/ws-tracking`

Client subscribe message:
```json
{ "type": "SUBSCRIBE", "raceId": "<race-uuid>" }
```

Server location update message:
```json
{
  "type": "LOCATION_UPDATE",
  "raceId": "<race-uuid>",
  "data": {
    "participantId": "<uuid>",
    "bibNumber": "A-102",
    "participantName": "Runner Name",
    "latitude": -33.9249,
    "longitude": 18.4241,
    "speed": 12.5,
    "lastCheckpoint": "CP-2",
    "elapsedTime": "00:42:10",
    "status": "STARTED"
  }
}
```

## API Documentation
Swagger UI:
- `/api/swagger-ui/index.html`

OpenAPI JSON:
- `/api/v3/api-docs`

## Tests
```bash
cd apps/backend
mvn clean test
```

Current test suite includes controller-level smoke tests under:
- `src/test/java/com/racetracker/controller`

Focused operational tests:
```bash
cd apps/backend
mvn -Dtest=ActuatorSecurityIntegrationTest,TrackingObservationCoverageTest test
```
