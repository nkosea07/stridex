# StrideX Modernization Roadmap Design

## Status

Approved on 2026-05-31 as a 3-6 month modernization direction optimized for:
- public spectator growth first
- organizer/timer reliability as the operational constraint

This document is the design/spec artifact. It is not the implementation plan.

## Problem

StrideX already has a modern-enough stack and real product momentum, but the platform is uneven:
- the new public spectator surface exists, but the surrounding web architecture is still mostly SPA-style and admin-first
- mobile has good Expo/React Native primitives, but it is not yet using the strongest current runtime and update patterns
- backend supports tracking, auth, and public read models, but still lacks stronger modular boundaries, deeper observability, and a modern ingest foundation for GPS plus RFID
- geospatial and event-heavy parts of the system are still treated like standard CRUD/data flows rather than race-tech infrastructure

The goal is to modernize the app without rewriting it.

## Recommendation

Use an upgrade-in-place strategy.

Do not rewrite the monorepo, replace Expo, or split the backend into microservices. Instead:
- modernize web routing and data loading incrementally
- upgrade the mobile runtime and background/update model
- upgrade the backend platform and observability base
- introduce geospatial and ingest/event architecture where it materially improves race-day operations

This approach provides the best tradeoff between speed, risk, and product continuity.

## Rejected Approaches

### 1. Platform-first backend heavy modernization

Pros:
- strongest long-term core
- best for RFID/timer complexity

Cons:
- delays visible product wins
- weaker support for immediate spectator growth

### 2. Public-product-first acceleration without platform discipline

Pros:
- fast marketing progress
- visible UI gains quickly

Cons:
- likely to create a second layer of technical debt
- weak foundation for organizer reliability and RFID maturity

## Target Modernization Model

For the next 3-6 months, StrideX should evolve along five coordinated tracks.

### 1. Experience Track

- `apps/web` becomes a dual-surface product:
  - public spectator routes
  - authenticated operator/admin routes
- `apps/mobile` remains the richer logged-in athlete experience, with stronger alerts, offline behavior, and update delivery

### 2. Platform Track

- web moves toward React Router framework features on top of the existing Vite app
- mobile moves from Expo SDK 53 to 54 as the next safe modernization target
- backend moves from Spring Boot 3.2.x to 3.5.x before any Boot 4 decision

### 3. Data Track

- route/course/proximity logic moves toward PostGIS-backed geospatial operations
- high-volume telemetry/history tables move toward partitioning
- prediction and live tracking are treated as data products, not just controller responses

### 4. Observability Track

- OpenTelemetry is added across browser, mobile, and backend
- race-day flows become measurable:
  - page load and revalidation
  - websocket/session health
  - GPS ingest lag
  - checkpoint/RFID processing lag
  - notification delivery and sync errors

### 5. Ingest Track

- public and operator UI can keep their current protocols initially
- internally, GPS, QR, manual checkpoint, and future RFID events move toward a cleaner event normalization pipeline

## Sequencing

### Phase 1: Stabilize and Modernize the Product Shell

Goal:
- make the existing public spectator and operator experiences easier to evolve

Includes:
- React Router framework adoption for public routes first
- React data loading/revalidation modernization
- Expo 54 upgrade
- Spring Boot 3.5 upgrade
- no large architectural rewrite

### Phase 2: Make Public and Mobile Tracking Feel Premium

Goal:
- improve conversion and product quality without adding heavy infrastructure first

Includes:
- stronger public race/athlete flows
- saved follows/favorites groundwork
- richer mobile alerts and local-first behavior
- projection-oriented APIs for public and mobile surfaces

### Phase 3: Modernize the Data and Operations Core

Goal:
- support bigger races and cleaner operational behavior

Includes:
- PostGIS
- partitioning for telemetry-heavy data
- OpenTelemetry
- cleaner event model for tracking sources

### Phase 4: Mature Ingest and Integration Architecture

Goal:
- support real-world timing/timer integration

Includes:
- RFID reader/station registry
- ingest auth and normalization
- replay and reconciliation
- operational dashboards
- evaluate Kafka or NATS only when justified

### Phase 5: Advanced Differentiation

Goal:
- add capabilities that make StrideX feel industry-leading

Includes:
- hybrid GPS + RFID prediction
- replay/storytelling
- announcer mode
- sponsor-safe public inventory
- AI-assisted ops features only after event quality and observability are strong

## Architecture Decisions

### Web

Recommended direction:
- keep Vite
- adopt React Router v7 framework features incrementally
- use route loaders/actions/revalidation for public routes first
- pre-render public race pages where it helps shareability and search
- keep operator/admin flows more client-heavy until patterns stabilize

Do not:
- rewrite to Next.js just to obtain SSR

### Mobile

Recommended direction:
- stay on Expo
- upgrade to Expo SDK 54
- make background sync, notifications, deep links, and OTA updates first-class
- keep SQLite as the local-first state anchor

Do not:
- eject from Expo unless a hard native dependency truly requires it

### Backend

Recommended direction:
- upgrade to Spring Boot 3.5 first
- formalize module boundaries with Spring Modulith
- add observability before major distributed infrastructure

Do not:
- split into microservices during this horizon

### Data and Messaging

Recommended direction:
- PostgreSQL remains primary system of record
- add PostGIS for geospatial operations
- add partitioning for telemetry-scale tables
- keep client-facing WebSocket behavior if adequate
- introduce Kafka or NATS only when ingest complexity or scale proves the need

## Success Criteria

At the roadmap level, success means:
- public race pages are faster, more shareable, and easier to evolve
- mobile updates, sync, and alerts become more reliable
- race-day issues become observable rather than anecdotal
- the backend is structurally ready for RFID/timer ingest without prematurely overbuilding
- the modernization work reduces platform risk while increasing product velocity

## Planned Follow-on Implementation Plans

This design intentionally spans multiple subsystems. Implementation should be broken into separate plans, starting with the lowest-risk foundation:

1. web routing/data modernization foundation
2. mobile runtime and offline modernization foundation
3. backend platform and observability modernization foundation
4. geospatial and telemetry data modernization
5. RFID/bib ingest architecture implementation

The first implementation plan should cover only the foundation phase, not the full program.
