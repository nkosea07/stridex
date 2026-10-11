# StrideX Backend Platform And Observability Foundation Design

## Status

Prepared on 2026-05-31 as the next modernization step after the completed web routing/data foundation.

## Why This Is Next

The public spectator web surface is now structurally modernized, but it still depends on a backend that is uneven in three important ways:
- the Spring Boot base is still on `3.2.2`
- the service has no first-class management/health surface for deployments and race-day operations
- tracking and public-read hot paths are not instrumented in a way that makes latency, error rates, and race-day regressions easy to see

Upgrading mobile next would improve the athlete app, but it would not strengthen the shared backend that now serves both public spectators and operators. For the current priority order, the backend is the higher-leverage next move.

## Problem

`apps/backend` already has the right product domains:
- auth and session management
- race and participant management
- live tracking and public spectator read models
- WebSocket fanout
- Redis-backed tracking fanout support

But it is still missing a modern operational baseline:
- no Actuator health/readiness/liveness endpoints
- no metrics scrape surface
- no consistent service-level observations around public tracking and ingest flows
- no explicit plan for secure management endpoint exposure
- no updated docs that explain operational behavior for the modernized public web surface

## Approach Options

### 1. Recommended: Foundation Upgrade + Management Surface + Service Observations

Scope:
- upgrade Spring Boot from `3.2.2` to `3.5.x`
- add Actuator and a Prometheus metrics scrape endpoint
- add Micrometer observation support with targeted `@Observed` instrumentation on tracking/public-service hot paths
- expose only safe management endpoints anonymously
- update backend/root docs and targeted tests

Why this is the right next step:
- directly supports the new public spectator surface
- creates deployment and race-day visibility without forcing a distributed tracing rollout yet
- keeps scope bounded to one backend foundation plan

### 2. Heavier: Full OpenTelemetry Exporter Rollout Now

Scope:
- everything in Option 1 plus exporter wiring, trace propagation policy, collector assumptions, and environment-specific telemetry shipping

Why not yet:
- stronger long-term, but it pulls in infrastructure assumptions that are not yet encoded in this repo
- higher chance of mixing app code work with deployment/platform work too early

### 3. Minimal: Spring Boot Upgrade Only

Scope:
- dependency refresh and compatibility fixes, no management/observability work

Why not:
- lowest risk technically, but it leaves the platform with the same operational blind spots
- not enough value for the next modernization slot

## Recommendation

Use Option 1.

This phase should modernize the backend platform enough that:
- health checks and readiness are explicit
- metrics are available for scrape/inspection
- public tracking and ingest hot paths are observable
- the public spectator web surface is backed by a safer production baseline

## Planned Scope

### In Scope

- `apps/backend` Spring Boot platform upgrade to `3.5.x`
- dependency alignment needed to keep the service building and tests passing
- management endpoint configuration in `application.yml`
- secure exposure of:
  - `/actuator/health`
  - `/actuator/health/liveness`
  - `/actuator/health/readiness`
  - `/actuator/info`
  - `/actuator/prometheus` as an authenticated-only management endpoint
- targeted observation on:
  - `PublicTrackingServiceImpl`
  - `TrackingServiceImpl`
  - optionally the WebSocket/Redis live fanout seam if the code path stays small
- targeted controller/security tests for management endpoints
- docs updates in:
  - `README.md`
  - `DEVELOPMENT_GUIDE.md`
  - `apps/backend/README.md`

### Explicitly Out Of Scope

- Expo/mobile upgrade
- PostGIS or database partitioning
- RFID/bib ingest device registry
- Kafka/NATS/event backbone work
- full distributed tracing exporter/collector rollout
- backend module extraction or a microservice split

## Architecture Direction

### Platform

Keep the backend as a modular monolith. This phase is not about changing the domain layout. It is about upgrading the runtime and adding an operational baseline.

### Observability

Use Spring Boot Actuator plus Micrometer observation primitives already aligned with modern Spring conventions.

The first observation targets should be the flows that matter most to the new product surface:
- public race hub reads
- public athlete/live tracking reads
- GPS location writes
- checkpoint writes
- live tracking aggregate reads

This gives immediate value for latency/error visibility without needing a large telemetry architecture decision yet.

### Security

Management exposure should be narrow:
- health and probe endpoints may be anonymous
- `info` may be anonymous if it contains only safe build/app metadata
- metrics scrape endpoints should not be public

This keeps Cloud Run or similar probes simple while avoiding accidental public management exposure.

## Success Criteria

This foundation is successful when:
- the backend builds and the targeted test suite passes on the upgraded Spring Boot baseline
- liveness/readiness/info endpoints exist and are documented
- public tracking and tracking-write hot paths are instrumented with clear observation names
- the security rules around management endpoints are explicit and tested
- the docs describe how this backend foundation supports the public spectator web surface

## Follow-On After This Plan

After this foundation lands, the next backend-adjacent modernization steps should be split again:
1. geospatial and telemetry data modernization
2. RFID/bib ingest modernization
3. deeper OpenTelemetry/exporter rollout if deployment infrastructure is ready
