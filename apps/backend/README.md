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

API base path is configured as `/api`.

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
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/auth/me`
- `PUT /api/auth/profile`

### Races
- `GET /api/races`
- `GET /api/races/{id}`
- `POST /api/races`
- `PUT /api/races/{id}`
- `PATCH /api/races/{id}/status`
- `DELETE /api/races/{id}`

### Participants and Registration
- `GET /api/races/{raceId}/participants`
- `GET /api/races/{raceId}/participants/{id}`
- `POST /api/races/{raceId}/participants`
- `PUT /api/races/{raceId}/participants/{id}`
- `DELETE /api/races/{raceId}/participants/{id}`
- `POST /api/races/{raceId}/participants/{id}/check-in`
- `POST /api/races/{raceId}/participants/import`
- `GET /api/races/{raceId}/participants/export`
- `POST /api/races/{raceId}/register`
- `GET /api/races/my-registrations`
- `DELETE /api/registrations/{registrationId}`

### Checkpoints
- `GET /api/races/{raceId}/checkpoints`
- `POST /api/races/{raceId}/checkpoints`
- `PUT /api/races/{raceId}/checkpoints/{checkpointId}`
- `DELETE /api/races/{raceId}/checkpoints/{checkpointId}`
- `POST /api/races/{raceId}/checkpoints/{checkpointId}/record`

### Tracking
- `POST /api/tracking/{raceId}/location`
- `POST /api/tracking/{raceId}/locations`
- `POST /api/tracking/{raceId}/checkpoint`
- `GET /api/races/{raceId}/tracking/live`
- `GET /api/races/{raceId}/tracking/{participantId}`

### Results
- `GET /api/races/{raceId}/results`
- `GET /api/races/{raceId}/results/{participantId}`
- `GET /api/results/my`
- `POST /api/races/{raceId}/results/publish`

### Dashboard and Notifications
- `GET /api/dashboard/stats`
- `GET /api/notifications`
- `PUT /api/notifications/{id}/read`

## WebSocket (Live Tracking)
Endpoint:
- `ws://<host>/api/ws/tracking`

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
