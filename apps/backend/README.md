# Race Tracker Backend

Spring Boot API for race administration, participant tracking, results, and notifications.

## Stack
- Java 17
- Spring Boot 3.2
- Spring Security (JWT)
- Spring Data JPA (PostgreSQL)
- Spring WebSocket
- MapStruct
- springdoc OpenAPI + Swagger UI

## Run
```bash
cd apps/backend
mvn spring-boot:run
```

The backend runs under the `/api` servlet context path, so versioned controller routes are exposed under `/api/v1`.

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
