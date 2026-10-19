# Architecture

Race Tracker / StrideX is a pnpm + Maven monorepo.

```
web (React Router 7, ssr:false) ─┐
mobile (Expo React Native) ──────┼──> backend (Spring Boot, /api, port 8081) ──> PostgreSQL/PostGIS
rfid-gateway (Node edge) ────────┘                                             └─> Redis (cache + streams)
rfid-reader-* (Java adapters) ───┘
```

## Backend layering

`controller` (HTTP, validation, `@PreAuthorize`) → `service` (+ `impl`) → `repository`.
DTOs + MapStruct mappers cross the API boundary; entities never leak. Authz is enforced
server-side: role gates via `@PreAuthorize`, per-race ownership via `RaceAccessGuard`.

### Hot paths

- **Tracking ingest** (`TrackingServiceImpl`): GPS location + checkpoint ingest, drift
  filtering, WebSocket fan-out, Micrometer `@Observed`.
- **RFID bridge**: readers post to `/api/v1/rfid/read-events`; events persist as
  `rfid_read_events`, publish to a Redis Stream, and are normalized asynchronously into
  `checkpoint_records`. `RfidStreamWorker` drains the stream; `RfidReadEventProcessor`
  resolves credentials and applies cutoffs.
- **Notifications**: `NotificationService.notify` persists an in-app row and hands off to
  `NotificationDispatcher`, which fans out to `NotificationChannel` adapters (push/email/
  sms/whatsapp) after commit, honoring `notification_preferences`, and writes a
  `notification_deliveries` audit row per channel.
- **Results**: `ResultServiceImpl.publish` snapshots computed results into `race_results`;
  published reads serve the snapshot under a revision guard, falling back to compute.
- **Imports**: `ParticipantServiceImpl.importCsv` stores the upload and hands off to
  `ParticipantImportProcessor` (`@Async`), which resumes/retries persisted `import_jobs`.

### Cross-cutting

- Auth: JWT access/refresh (browser cookies for web), verified-email password login,
  lockout,
  rate limiting, append-only audit events, correlation IDs.
- Config: `application.yml` + `RaceTrackerProperties`; `ProductionConfigurationValidator`
  rejects insecure production config at startup.
- Observability: actuator (`/api/actuator/**`), Prometheus metrics, `@Observed` timings.
- Contract: `packages/contracts/contract.json` is the source of truth; `pnpm contracts:validate`
  cross-checks backend version, security config, and both clients.

## Data

Flyway is the only schema source (`db/migration`). `database/schema.sql` is reference only.
See `er-diagram.md`. High-volume tables: `gps_locations` (retention via `GpsRetentionService`;
range partitioning on `recorded_at` remains a dedicated ops migration), `rfid_read_events`,
`notification_deliveries`, and append-only audit events.

## Testing

- Backend: JUnit 5 + Mockito (`mvn -B test`); PostGIS Flyway smoke test gated by
  `FLYWAY_TEST_*`.
- Web: Vitest + Testing Library; Mobile: Vitest (recovery suite); Gateway: node:test.
- Load: `tests/load/tracking-load.js` (k6). E2E: `tests/e2e` (Cypress, not in default CI).

CI (`.github/workflows/pilot-validation.yml`): contracts → web → mobile → gateway → backend
(Java 17 + PostGIS) → Docker image builds. Deploy is manual (`.github/workflows/deploy.yml`).
