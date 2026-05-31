# StrideX Backend Platform And Observability Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade `apps/backend` to a more modern Spring Boot baseline and add a safe management/observability foundation that supports the new public spectator web surface.

**Architecture:** Keep the backend as a modular monolith. Modernize in place by upgrading the Spring Boot platform, adding Actuator-based management endpoints, wiring Micrometer observation support with targeted `@Observed` instrumentation on public tracking and ingest hot paths, and tightening the security/docs/tests around that operational surface.

**Tech Stack:** Java 17, Spring Boot 3.5.x, Spring Security, Spring WebSocket, Micrometer Observation, Spring Boot Actuator, Prometheus endpoint exposure, Maven, JUnit/WebMvc tests

---

## Scope Guardrail

This plan is intentionally limited to the backend platform and observability foundation only.

Do in this plan:
- Spring Boot platform upgrade for `apps/backend`
- dependency alignment required by that upgrade
- Actuator health/info/metrics surface
- targeted service-level observation for public tracking and tracking writes
- management endpoint security rules and tests
- docs updates tied to this backend modernization

Do **not** do in this plan:
- mobile/Expo upgrades
- PostGIS adoption
- telemetry table partitioning
- RFID/bib device ingest
- Kafka/NATS/event bus work
- full external OpenTelemetry collector/exporter rollout
- broad service/package restructuring

Follow-on plans should remain separate for:
- geospatial and telemetry data modernization
- RFID/bib ingest modernization
- mobile runtime modernization

## File Map

### Modify

- `apps/backend/pom.xml`
- `apps/backend/src/main/resources/application.yml`
- `apps/backend/src/main/java/com/racetracker/config/SecurityConfig.java`
- `apps/backend/src/main/java/com/racetracker/service/impl/PublicTrackingServiceImpl.java`
- `apps/backend/src/main/java/com/racetracker/service/impl/TrackingServiceImpl.java`
- `apps/backend/README.md`
- `README.md`
- `DEVELOPMENT_GUIDE.md`

### Create

- `apps/backend/src/test/java/com/racetracker/config/ActuatorSecurityIntegrationTest.java`
- `apps/backend/src/test/java/com/racetracker/service/TrackingObservationCoverageTest.java`

### Optional modifies if required by the Spring Boot 3.5 upgrade

- `apps/backend/src/main/java/com/racetracker/config/ObservabilityConfig.java`
- `apps/backend/src/main/java/com/racetracker/config/WebSocketConfig.java`
- `apps/backend/src/main/java/com/racetracker/config/RedisConfig.java`
- `apps/backend/src/main/java/com/racetracker/security/JwtAuthenticationFilter.java`
- `apps/backend/src/test/java/com/racetracker/config/ActuatorSecurityIntegrationTest.java`
- `apps/backend/src/test/java/com/racetracker/controller/PublicTrackingControllerWebMvcTest.java`
- `apps/backend/src/test/java/com/racetracker/controller/PublicTrackingControllerSecurityTest.java`
- `apps/backend/src/test/java/com/racetracker/controller/TrackingControllerWebMvcTest.java`
- `apps/backend/src/test/java/com/racetracker/controller/ControllerAuthorizationMatrixTest.java`

## Intended Outcome

After this plan:
- `apps/backend` runs on a current Spring Boot `3.5.x` line
- the service exposes a safe operational surface under `/api/actuator`
- readiness/liveness and safe build info are available without opening the rest of management publicly
- public race hub and tracking hot paths are instrumented with named observations
- tracking write paths are instrumented with named observations
- targeted tests prove management endpoint security and the observation wiring still boots

## Task 1: Upgrade The Backend Platform Baseline

**Files:**
- Modify: `apps/backend/pom.xml`
- Optional modify: compatibility files listed in the optional section above

- [ ] **Step 1: Record the current backend baseline**

Write down in working notes:
- current Spring Boot parent version in `apps/backend/pom.xml`
- current Java version
- whether Actuator is absent
- whether Micrometer observation/tracing dependencies are absent

- [ ] **Step 2: Run the current backend verification baseline**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=PublicTrackingControllerSecurityTest,PublicTrackingControllerWebMvcTest,TrackingControllerWebMvcTest,ControllerAuthorizationMatrixTest,AuthControllerWebMvcTest test
```

Expected: PASS

- [ ] **Step 3: Upgrade the Spring Boot parent and add the minimum observability dependencies**

Update `apps/backend/pom.xml` to:
- upgrade the Spring Boot parent from `3.2.2` to a `3.5.x` release
- add `spring-boot-starter-actuator`
- add `spring-boot-starter-aop`
- add `micrometer-registry-prometheus`

Keep the rest of the dependency graph as stable as possible.

Only if the Spring Boot upgrade forces it:
- raise `springdoc-openapi-starter-webmvc-ui` to a Boot-3.5-compatible release

- [ ] **Step 4: Run the targeted backend test baseline again**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=PublicTrackingControllerSecurityTest,PublicTrackingControllerWebMvcTest,TrackingControllerWebMvcTest,ControllerAuthorizationMatrixTest,AuthControllerWebMvcTest test
```

Expected: PASS

If it fails due to upgrade compatibility:
- fix only the compatibility breakage needed for this plan
- do not widen scope into unrelated refactors

- [ ] **Step 5: Commit the platform upgrade**

```bash
git add /Users/nkosinxumalo/WebProjects/AMS/apps/backend/pom.xml
git commit -m "chore(backend): upgrade platform baseline"
```

Note:
- only stage files that actually changed
- do not force unrelated files into this commit
- if the Boot upgrade required compatibility edits outside `pom.xml`, stage only those exact files too

## Task 2: Add The Management Endpoint Foundation

**Files:**
- Modify: `apps/backend/src/main/resources/application.yml`
- Modify: `apps/backend/src/main/java/com/racetracker/config/SecurityConfig.java`
- Create: `apps/backend/src/test/java/com/racetracker/config/ActuatorSecurityIntegrationTest.java`

- [ ] **Step 1: Write the failing management-security test**

Create `ActuatorSecurityIntegrationTest.java` as a Spring Boot integration test using `@SpringBootTest` plus `@AutoConfigureMockMvc` to assert:
- `GET /api/actuator/health` is allowed
- `GET /api/actuator/health/liveness` is allowed
- `GET /api/actuator/health/readiness` is allowed
- `GET /api/actuator/info` is allowed only if the payload is intentionally safe
- `GET /api/actuator/prometheus` is **not** publicly accessible

Do not model this as a `@WebMvcTest` slice. Actuator endpoints are framework-managed endpoints, not the app controllers in `com.racetracker.controller`.

Build the test with an isolated management-focused context:
- use an inner `@SpringBootConfiguration` / `@EnableAutoConfiguration` test application if needed
- set test properties for `jwt.secret`, `racetracker.api.version`, `cors.allowed-origins`, and `server.servlet.context-path`
- explicitly exclude infrastructure auto-config that would otherwise require live Postgres/Redis/mail during startup
- mock only the security beans that are necessary to construct the filter chain cleanly

The goal is to validate management endpoint security behavior, not database wiring.

- [ ] **Step 2: Run the new test and confirm it fails**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=ActuatorSecurityIntegrationTest test
```

Expected: FAIL because the management endpoint surface is not configured yet

- [ ] **Step 3: Add management configuration**

Update `application.yml` to add a `management:` section that:
- enables probe support
- exposes only the endpoints needed for this phase
- keeps health detail visibility conservative
- enables the Prometheus scrape endpoint
- enables safe app/build info exposure

Prefer `/api/actuator` under the existing servlet context path rather than inventing a second management base path.

- [ ] **Step 4: Update the security filter chain for management endpoints**

Update `SecurityConfig.java` so:
- `/actuator/health/**` remains anonymous
- `/actuator/info` is anonymous only if the response is intentionally non-sensitive
- `/actuator/prometheus` requires authentication
- existing `/api/v1/public/races/**` public rules keep working

Do not weaken any existing auth rule outside the management surface.

- [ ] **Step 5: Run the new management-security test and the auth/public matrix tests**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=ActuatorSecurityIntegrationTest,ControllerAuthorizationMatrixTest,PublicTrackingControllerSecurityTest test
```

Expected: PASS

- [ ] **Step 6: Commit the management foundation**

```bash
git add /Users/nkosinxumalo/WebProjects/AMS/apps/backend/src/main/resources/application.yml
git add /Users/nkosinxumalo/WebProjects/AMS/apps/backend/src/main/java/com/racetracker/config/SecurityConfig.java
git add /Users/nkosinxumalo/WebProjects/AMS/apps/backend/src/test/java/com/racetracker/config/ActuatorSecurityIntegrationTest.java
git commit -m "feat(backend): add management endpoint foundation"
```

## Task 3: Add Observation Wiring And Instrument The Tracking Hot Paths

**Files:**
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/PublicTrackingServiceImpl.java`
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/TrackingServiceImpl.java`
- Create: `apps/backend/src/test/java/com/racetracker/service/TrackingObservationCoverageTest.java`
- Optional modify/create: `apps/backend/src/main/java/com/racetracker/config/ObservabilityConfig.java`

- [ ] **Step 1: Write the failing observation coverage test**

Create `TrackingObservationCoverageTest.java` as a focused reflection-based test that verifies the target methods in:
- `PublicTrackingServiceImpl`
- `TrackingServiceImpl`

are annotated with `@Observed` and use the expected stable observation names.

- [ ] **Step 2: Run the observation coverage test and confirm it fails**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=TrackingObservationCoverageTest test
```

Expected: FAIL because the target methods are not instrumented yet

- [ ] **Step 3: Instrument the public and tracking hot paths**

Use `@Observed` on the service methods that matter most to the new public/product surface:
- in `PublicTrackingServiceImpl`:
  - `getRaceHub`
  - `getLiveAthletes`
  - `getAthleteTracking`
- in `TrackingServiceImpl`:
  - `recordLocation`
  - `recordLocationsBatch`
  - `recordCheckpoint`
  - `getLiveData`
  - `getParticipantTrack`

Give the observations stable, explicit names such as:
- `tracking.public.race_hub`
- `tracking.public.live_athletes`
- `tracking.public.athlete_detail`
- `tracking.ingest.location`
- `tracking.ingest.location_batch`
- `tracking.ingest.checkpoint`
- `tracking.live.aggregate`
- `tracking.live.participant_track`

Prefer low-cardinality tags only. Do not add raw participant IDs, emails, or query strings as tags.

- [ ] **Step 4: Add an observability compatibility shim only if Boot 3.5 does not auto-configure `@Observed` support cleanly**

If the upgraded app does **not** create the `ObservedAspect` automatically once Actuator + AOP are present:
- create `apps/backend/src/main/java/com/racetracker/config/ObservabilityConfig.java`
- keep it minimal and application-side only
- do not add exporter or collector-specific wiring

If Boot already provides the needed support, skip this file entirely.

- [ ] **Step 5: Run the observation coverage test and the public/tracking controller tests**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=TrackingObservationCoverageTest,PublicTrackingControllerSecurityTest,PublicTrackingControllerWebMvcTest,TrackingControllerWebMvcTest test
```

Expected: PASS

- [ ] **Step 6: Commit the observation foundation**

```bash
git add /Users/nkosinxumalo/WebProjects/AMS/apps/backend/src/main/java/com/racetracker/service/impl/PublicTrackingServiceImpl.java
git add /Users/nkosinxumalo/WebProjects/AMS/apps/backend/src/main/java/com/racetracker/service/impl/TrackingServiceImpl.java
git add /Users/nkosinxumalo/WebProjects/AMS/apps/backend/src/test/java/com/racetracker/service/TrackingObservationCoverageTest.java
git commit -m "feat(backend): instrument tracking hot paths"
```

If `ObservabilityConfig.java` was required, stage that file too.

## Task 4: Document The Backend Operational Surface

**Files:**
- Modify: `apps/backend/README.md`
- Modify: `README.md`
- Modify: `DEVELOPMENT_GUIDE.md`

- [ ] **Step 1: Note the doc delta before editing**

The docs must explain:
- the backend now runs on the modernized Spring Boot baseline
- management endpoints exist under `/api/actuator`
- which management endpoints are public vs authenticated
- that observations now exist for public tracking and ingest hot paths
- that exporter/collector rollout, geospatial work, and RFID ingest remain later plans

- [ ] **Step 2: Update `apps/backend/README.md`**

Add concise notes covering:
- management endpoints and probe paths
- metrics/observability foundation
- safe expectations around `/api/actuator`
- current backend run/test commands

- [ ] **Step 3: Update root docs**

Update `README.md` and `DEVELOPMENT_GUIDE.md` so they describe:
- why the backend platform/observability phase follows the web modernization phase
- what this phase actually shipped
- what is still deferred

- [ ] **Step 4: Run the full phase validation**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=ActuatorSecurityIntegrationTest,TrackingObservationCoverageTest,PublicTrackingControllerSecurityTest,PublicTrackingControllerWebMvcTest,TrackingControllerWebMvcTest,ControllerAuthorizationMatrixTest,AuthControllerWebMvcTest test
```

Then run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn test
```

Expected:
- targeted test suite PASS
- full backend test suite PASS

- [ ] **Step 5: Commit the docs and validation close-out**

```bash
git add /Users/nkosinxumalo/WebProjects/AMS/apps/backend/README.md
git add /Users/nkosinxumalo/WebProjects/AMS/README.md
git add /Users/nkosinxumalo/WebProjects/AMS/DEVELOPMENT_GUIDE.md
git commit -m "docs(backend): document observability foundation"
```

## Execution Notes

- Keep the scope tight. This plan should not turn into a full tracing platform rollout.
- Prefer Spring-native observability primitives over custom instrumentation wrappers.
- Avoid sensitive or high-cardinality observation tags.
- Do not open management endpoints broadly just because Actuator is present.
- Treat the new public spectator web surface as the primary consumer whose reliability this phase is improving.

## Follow-On Plans

After this plan ships, create or execute:
- `2026-05-31-geospatial-telemetry-modernization.md`
- `2026-05-31-rfid-bib-ingest-modernization.md`
- `2026-05-31-mobile-runtime-modernization-foundation.md`
