# StrideX Geospatial And Telemetry Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a PostGIS-backed geospatial foundation and safer telemetry-read path for live race tracking without changing existing web/mobile API contracts.

**Architecture:** Keep the current Spring Boot modular monolith and existing decimal latitude/longitude DTOs. Add PostGIS extension support, geography point columns/indexes, a small backend spatial query/helper boundary, tests that prove schema/query readiness, and documentation for the future `gps_locations` partition cutover. Do not implement RFID ingest or rewrite route prediction in this phase.

**Tech Stack:** Java 17, Spring Boot 3.5.x, Spring Data JPA, PostgreSQL 15+, PostGIS, native SQL repository queries where useful, JUnit/WebMvc/service tests, Maven

---

## Scope Guardrail

Do in this plan:

- PostGIS extension and spatial schema foundation
- geography point columns for races, checkpoints, and GPS locations
- GiST spatial indexes
- coordinate validity checks only where safe
- backend spatial helper/repository boundary for proximity and distance-ready queries
- tests proving current tracking behavior still works
- docs for geospatial foundation and telemetry partitioning path

Do **not** do in this plan:

- RFID/bib reader ingest
- Kafka/NATS/event bus work
- forced conversion of `gps_locations` into a partitioned table
- route polyline/course geometry editor
- mobile or web UI redesign
- API response shape changes
- removal of existing latitude/longitude columns

## File Map

### Modify

- `apps/backend/database/schema.sql`
- `apps/backend/src/main/java/com/racetracker/model/Race.java`
- `apps/backend/src/main/java/com/racetracker/model/Checkpoint.java`
- `apps/backend/src/main/java/com/racetracker/model/GpsLocation.java`
- `apps/backend/src/main/java/com/racetracker/repository/GpsLocationRepository.java`
- `apps/backend/src/main/java/com/racetracker/repository/CheckpointRepository.java`
- `apps/backend/src/main/java/com/racetracker/service/impl/TrackingServiceImpl.java`
- `apps/backend/README.md`
- `README.md`
- `DEVELOPMENT_GUIDE.md`

### Create

- `apps/backend/src/main/java/com/racetracker/service/GeoTelemetryService.java`
- `apps/backend/src/main/java/com/racetracker/service/impl/GeoTelemetryServiceImpl.java`
- `apps/backend/src/test/java/com/racetracker/database/GeospatialSchemaTest.java`
- `apps/backend/src/test/java/com/racetracker/service/GeoTelemetryServiceTest.java`

Only create additional files if the implementation becomes hard to test or the existing repository interfaces become too broad.

## Intended Outcome

After this plan:

- PostGIS is enabled by the schema.
- `races`, `checkpoints`, and `gps_locations` have spatial query fields while keeping existing decimal coordinates.
- spatial indexes exist for future `ST_DWithin` and distance queries.
- backend code exposes a narrow geospatial helper/service boundary.
- existing tracking APIs and DTOs remain stable.
- the future partitioning path is documented, not prematurely executed.

## Task 1: Add PostGIS Schema Foundation

**Files:**

- Modify: `apps/backend/database/schema.sql`
- Create: `apps/backend/src/test/java/com/racetracker/database/GeospatialSchemaTest.java`

- [ ] **Step 1: Write the failing schema test**

Create `GeospatialSchemaTest.java` that reads `apps/backend/database/schema.sql` as text and asserts it contains:

- `CREATE EXTENSION IF NOT EXISTS postgis`
- `geography(Point, 4326)` or `geography(POINT,4326)` spatial columns for race start/finish, checkpoints, and GPS locations
- `USING GIST` indexes for those spatial fields
- a documented partitioning path comment for `gps_locations`

Use a text-level test because this repository currently uses `schema.sql`, not Flyway/Liquibase migrations.

- [ ] **Step 2: Run the failing test**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=GeospatialSchemaTest test
```

Expected: FAIL because the schema has no PostGIS extension or geography columns yet.

- [ ] **Step 3: Enable PostGIS and add spatial columns**

Update `apps/backend/database/schema.sql`.

Near the UUID extension:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

Add compatible spatial columns while keeping decimal fields:

```sql
ALTER TABLE races
    ADD COLUMN IF NOT EXISTS start_location_geog geography(Point, 4326),
    ADD COLUMN IF NOT EXISTS finish_location_geog geography(Point, 4326);

ALTER TABLE checkpoints
    ADD COLUMN IF NOT EXISTS location_geog geography(Point, 4326);

ALTER TABLE gps_locations
    ADD COLUMN IF NOT EXISTS location_geog geography(Point, 4326);
```

If editing the create-table definitions is cleaner than later `ALTER TABLE` blocks, add equivalent columns directly to each table definition and keep idempotent comments for existing deployments.

- [ ] **Step 4: Populate spatial columns for existing rows**

Add idempotent backfill SQL:

```sql
UPDATE races
SET start_location_geog = ST_SetSRID(ST_MakePoint(start_location_lng::double precision, start_location_lat::double precision), 4326)::geography
WHERE start_location_lat IS NOT NULL
  AND start_location_lng IS NOT NULL
  AND start_location_geog IS NULL;

UPDATE races
SET finish_location_geog = ST_SetSRID(ST_MakePoint(finish_location_lng::double precision, finish_location_lat::double precision), 4326)::geography
WHERE finish_location_lat IS NOT NULL
  AND finish_location_lng IS NOT NULL
  AND finish_location_geog IS NULL;

UPDATE checkpoints
SET location_geog = ST_SetSRID(ST_MakePoint(longitude::double precision, latitude::double precision), 4326)::geography
WHERE latitude IS NOT NULL
  AND longitude IS NOT NULL
  AND location_geog IS NULL;

UPDATE gps_locations
SET location_geog = ST_SetSRID(ST_MakePoint(longitude::double precision, latitude::double precision), 4326)::geography
WHERE location_geog IS NULL;
```

- [ ] **Step 5: Add spatial indexes**

Add:

```sql
CREATE INDEX IF NOT EXISTS idx_races_start_location_geog
    ON races USING GIST (start_location_geog);

CREATE INDEX IF NOT EXISTS idx_races_finish_location_geog
    ON races USING GIST (finish_location_geog);

CREATE INDEX IF NOT EXISTS idx_checkpoints_location_geog
    ON checkpoints USING GIST (location_geog);

CREATE INDEX IF NOT EXISTS idx_gps_locations_location_geog
    ON gps_locations USING GIST (location_geog);
```

Keep the existing B-tree indexes.

- [ ] **Step 6: Add telemetry partitioning path comments**

Add a clear schema comment near `gps_locations`:

```sql
-- Future partitioning path:
-- gps_locations should be converted to RANGE partitioning on recorded_at in a dedicated migration.
-- Do not rewrite the table in this schema foundation phase; keep JPA/API compatibility first.
```

- [ ] **Step 7: Run schema test**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=GeospatialSchemaTest test
```

Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add apps/backend/database/schema.sql apps/backend/src/test/java/com/racetracker/database/GeospatialSchemaTest.java
git commit -m "feat(database): add geospatial schema foundation"
```

## Task 2: Map Spatial Columns Safely In JPA

**Files:**

- Modify: `apps/backend/src/main/java/com/racetracker/model/Race.java`
- Modify: `apps/backend/src/main/java/com/racetracker/model/Checkpoint.java`
- Modify: `apps/backend/src/main/java/com/racetracker/model/GpsLocation.java`

- [ ] **Step 1: Add read-only spatial column mappings**

Add string/object read-only mappings only if needed for Hibernate validation. Prefer not to introduce geometry object dependencies unless required.

Example pattern:

```java
@Column(name = "location_geog", insertable = false, updatable = false)
private String locationGeog;
```

If Hibernate cannot map `geography` to `String`, do not force it. Instead:

- leave the entity unchanged
- use native SQL queries for spatial operations
- keep `spring.jpa.hibernate.ddl-auto=validate` compatibility in mind for production

- [ ] **Step 2: Run backend compile/test smoke**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=TrackingControllerWebMvcTest,PublicTrackingControllerWebMvcTest test
```

Expected: PASS

- [ ] **Step 3: Commit if entity mappings changed**

If files changed:

```bash
git add apps/backend/src/main/java/com/racetracker/model/Race.java apps/backend/src/main/java/com/racetracker/model/Checkpoint.java apps/backend/src/main/java/com/racetracker/model/GpsLocation.java
git commit -m "chore(backend): expose spatial columns safely"
```

If entity mappings are not needed, skip this commit and note that native queries own spatial access.

## Task 3: Add GeoTelemetry Service Boundary

**Files:**

- Create: `apps/backend/src/main/java/com/racetracker/service/GeoTelemetryService.java`
- Create: `apps/backend/src/main/java/com/racetracker/service/impl/GeoTelemetryServiceImpl.java`
- Modify: `apps/backend/src/main/java/com/racetracker/repository/GpsLocationRepository.java`
- Modify: `apps/backend/src/main/java/com/racetracker/repository/CheckpointRepository.java`
- Create: `apps/backend/src/test/java/com/racetracker/service/GeoTelemetryServiceTest.java`

- [ ] **Step 1: Add service interface**

Create:

```java
package com.racetracker.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface GeoTelemetryService {
    BigDecimal calculateDistanceKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2);
    boolean isWithinMeters(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2, int meters);
}
```

The first implementation can use the current Haversine math so service behavior is testable without PostGIS in unit tests.

- [ ] **Step 2: Implement the service**

Create `GeoTelemetryServiceImpl` and move/copy the existing Haversine calculation from `TrackingServiceImpl`.

Requirements:

- return kilometers for distance
- handle null coordinates by returning `BigDecimal.ZERO` or `false` according to method semantics
- keep math behavior compatible with existing tracking distance accumulation

- [ ] **Step 3: Add repository native query hooks**

In `CheckpointRepository`, add a native query method for future checkpoint proximity:

```java
@Query(value = """
    select exists (
        select 1
        from checkpoints c
        where c.race_id = :raceId
          and c.id = :checkpointId
          and c.location_geog is not null
          and ST_DWithin(
              c.location_geog,
              ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
              :meters
          )
    )
    """, nativeQuery = true)
boolean isCheckpointWithinMeters(...);
```

Use `BigDecimal` for latitude/longitude params.

In `GpsLocationRepository`, add a native query method only if needed for current services. Do not replace existing latest-location queries unless there is a concrete use.

- [ ] **Step 4: Add unit tests**

Create `GeoTelemetryServiceTest` with:

- zero distance for identical coordinates
- approximate Cape Town short-distance sanity check
- false when any coordinate is null
- true/false threshold behavior for `isWithinMeters`

- [ ] **Step 5: Run tests**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=GeoTelemetryServiceTest test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/java/com/racetracker/service/GeoTelemetryService.java \
  apps/backend/src/main/java/com/racetracker/service/impl/GeoTelemetryServiceImpl.java \
  apps/backend/src/main/java/com/racetracker/repository/CheckpointRepository.java \
  apps/backend/src/main/java/com/racetracker/repository/GpsLocationRepository.java \
  apps/backend/src/test/java/com/racetracker/service/GeoTelemetryServiceTest.java
git commit -m "feat(backend): add geo telemetry service boundary"
```

## Task 4: Use GeoTelemetry Boundary In Tracking Distance

**Files:**

- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/TrackingServiceImpl.java`
- Modify: `apps/backend/src/test/java/com/racetracker/controller/TrackingControllerWebMvcTest.java` if constructor/test wiring needs update

- [ ] **Step 1: Inject GeoTelemetryService**

Update `TrackingServiceImpl` constructor to accept `GeoTelemetryService`.

Replace local `calculateDistance(...)` calls with:

```java
geoTelemetryService.calculateDistanceKm(...).doubleValue()
```

or keep as `BigDecimal` where cleaner.

- [ ] **Step 2: Remove private duplicate Haversine method**

Delete `TrackingServiceImpl.calculateDistance(...)` after all usages move to the service.

- [ ] **Step 3: Run tracking tests**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=TrackingControllerWebMvcTest,TrackingObservationCoverageTest test
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/main/java/com/racetracker/service/impl/TrackingServiceImpl.java \
  apps/backend/src/test/java/com/racetracker/controller/TrackingControllerWebMvcTest.java
git commit -m "refactor(backend): centralize tracking distance math"
```

## Task 5: Document Telemetry Partitioning And Geospatial Operations

**Files:**

- Modify: `apps/backend/README.md`
- Modify: `README.md`
- Modify: `DEVELOPMENT_GUIDE.md`

- [ ] **Step 1: Update backend docs**

Document:

- PostGIS is required for production database setup
- spatial columns are query fields; decimal lat/lng remain API compatibility fields
- `ST_DWithin`/GiST are the intended proximity-query path
- `gps_locations` partitioning is a future dedicated migration

- [ ] **Step 2: Update root docs**

Add geospatial modernization status to `README.md`.

- [ ] **Step 3: Update development guide**

Add a section explaining:

- current geospatial foundation
- telemetry table growth risk
- future partition strategy by `recorded_at`
- RFID ingest remains deferred until this data foundation is in place

- [ ] **Step 4: Run final verification**

Run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS/apps/backend
mvn -Dtest=GeospatialSchemaTest,GeoTelemetryServiceTest,TrackingControllerWebMvcTest,PublicTrackingControllerWebMvcTest,TrackingObservationCoverageTest test
```

Then run:

```bash
cd /Users/nkosinxumalo/WebProjects/AMS
pnpm contracts:validate
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/backend/README.md README.md DEVELOPMENT_GUIDE.md
git commit -m "docs(backend): document geospatial telemetry foundation"
```

## Final Review Checklist

- [ ] `CREATE EXTENSION IF NOT EXISTS postgis` exists in `schema.sql`
- [ ] spatial point columns exist for race start/finish, checkpoints, and GPS locations
- [ ] GiST indexes exist for spatial columns
- [ ] current API DTOs remain unchanged
- [ ] tracking/public tracking tests pass
- [ ] docs explain partitioning as a future migration, not completed work
- [ ] RFID/bib ingest remains deferred

## Known Risks

- Existing deployments need PostGIS installed in the PostgreSQL instance before applying the schema.
- `schema.sql` is not a proper migration system. Production rollout should use a migration tool or carefully managed SQL migration before `ddl-auto=validate`.
- Converting `gps_locations` into a partitioned table is intentionally deferred because it can require data backfill, table rewrite, and deployment planning.
