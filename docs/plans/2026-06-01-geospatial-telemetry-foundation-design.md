# StrideX Geospatial And Telemetry Foundation Design

## Status

Approved on 2026-06-01 as the next modernization phase after the mobile runtime and offline tracking foundation.

This document is the design/spec artifact. It is not the implementation plan.

## Problem

StrideX stores race, checkpoint, and GPS coordinates as separate latitude/longitude decimal columns. That is enough for basic display, but it is weak for race-day operations:

- proximity queries are not index-backed geospatial operations
- distance/progress calculation is duplicated in Java with Haversine math
- `gps_locations` is a high-volume event table but is still shaped like normal CRUD data
- live tracking and public tracking reads rely on latest-point lookups without a dedicated telemetry projection
- future RFID/bib reconciliation will need cleaner event and telemetry foundations

The goal is to modernize the data foundation without rewriting tracking APIs or moving to a distributed event platform.

## Recommendation

Use an incremental PostGIS and telemetry-read-model foundation.

Do first:

- enable PostGIS in `schema.sql`
- add spatial point columns for race start/finish, checkpoints, and GPS locations
- add GiST indexes for point/proximity operations
- keep existing latitude/longitude fields and DTOs stable
- add backend helpers/repository queries that use database-backed spatial distance where it materially improves correctness or future readiness
- add an explicit partitioning path for `gps_locations` without forcing a risky table rewrite in the first pass
- document telemetry retention and partitioning expectations

Do not:

- rewrite all tracking calculations at once
- introduce Kafka/NATS
- implement RFID reader ingest
- change mobile/web contracts
- remove existing decimal coordinate columns

## Current Baseline

Backend:

- Spring Boot `3.5.x`
- PostgreSQL primary data store
- JPA entities for `Race`, `Checkpoint`, and `GpsLocation`
- `gps_locations` stores latitude/longitude, altitude, accuracy, speed, heading, battery level, recorded time, and received time
- tracking hot paths already have Micrometer `@Observed` names

Database:

- `races.start_location_lat/start_location_lng`
- `races.finish_location_lat/finish_location_lng`
- `checkpoints.latitude/longitude`
- `gps_locations.latitude/longitude`
- basic B-tree indexes on `gps_locations(participant_id)`, `gps_locations(race_id)`, `gps_locations(recorded_at)`, and `(latitude, longitude)`

The current schema does not enable PostGIS and does not define spatial indexes or generated spatial columns.

## External Reference Baseline

PostGIS supports spatial indexing through GiST indexes, and index-aware functions such as `ST_DWithin` are the correct foundation for “within a distance/radius” queries. The PostGIS documentation also recommends `geography(Point,4326)` where meter-based distance calculations are needed over lon/lat coordinates.

PostgreSQL declarative partitioning supports range partitioning by time-like columns, which fits high-volume GPS telemetry tables. This phase should prepare for partitioning and document the operational path, but not force an immediate production table rewrite.

Primary references:

- PostGIS spatial index FAQ: https://postgis.net/documentation/faq/spatial-indexes/
- PostGIS geography and distance documentation: https://postgis.net/docs/en/using_postgis_dbmanagement.html
- PostGIS `ST_DWithin`: https://postgis.net/docs/ST_DWithin.html
- PostgreSQL table partitioning: https://www.postgresql.org/docs/17/ddl-partitioning.html

## Target Architecture

### Database Shape

Keep decimal coordinate columns as compatibility fields. Add spatial columns as derived/query fields:

- `races.start_location_geog`
- `races.finish_location_geog`
- `checkpoints.location_geog`
- `gps_locations.location_geog`

Use `geography(Point,4326)` for meter-based race proximity and telemetry distance calculations.

Add GiST indexes:

- race start/finish location geography indexes
- checkpoint location geography index
- GPS location geography index

Keep existing B-tree indexes for participant/race/time filters.

### Telemetry Query Model

Add repository-level native queries where spatial operations matter:

- latest location per participant should stay optimized for current live tracking
- checkpoint proximity queries should use `ST_DWithin`
- participant track reads should keep ordering by `recorded_at`

The first pass should not replace every Haversine calculation. It should add a clear helper/service boundary so future route matching and predictions can move from Java math to PostGIS incrementally.

### Partitioning Path

Do not convert the existing `gps_locations` table to a partitioned table in this pass. That migration can rewrite data and requires deployment planning.

Instead:

- document the target range partition strategy by `recorded_at`
- add indexes that match the future partition key and live read patterns
- add a schema comment and docs section describing the future cutover
- keep JPA entity mapping stable

Future partitioning should happen with a dedicated migration plan that includes backfill, dual-write or maintenance window strategy, and rollback.

## Error Handling

Invalid coordinates should not be silently accepted into spatial calculations. The implementation should add basic validation or database constraints where safe:

- latitude between `-90` and `90`
- longitude between `-180` and `180`

If current data might violate this, use documentation and tests first, then add constraints only if they do not break existing seed/dev data.

## Testing Strategy

Testing should focus on proving the foundation exists and does not break current behavior:

- schema tests or text-level migration tests for PostGIS extension, geography columns, and GiST indexes
- repository/service tests for proximity helper behavior where possible
- existing tracking controller/service tests continue to pass
- full backend test suite remains green

## Success Criteria

This phase is complete when:

- `schema.sql` enables PostGIS and defines spatial fields/indexes
- current decimal coordinate fields and API DTOs remain backward compatible
- backend code has a small spatial helper/query boundary for future proximity and route work
- tracking/public tracking tests still pass
- docs describe the geospatial model and partitioning path
- RFID/bib ingest remains explicitly deferred

## Follow-Up Work

Separate future plans should cover:

- actual `gps_locations` table partition cutover
- route polyline/course geometry storage
- route matching and off-course detection
- hybrid GPS + RFID prediction
- RFID reader/station ingest and reconciliation
- telemetry retention automation
