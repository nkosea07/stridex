# Entity-relationship overview

Core domain (abridged; see Flyway migrations for the authoritative schema).

```mermaid
erDiagram
    USERS ||--o{ RACES : creates
    USERS }o--o{ RACES : "race_admins"
    RACES ||--o{ RACE_CATEGORIES : has
    RACES ||--o{ CHECKPOINTS : has
    RACES ||--o{ PARTICIPANTS : has
    RACES ||--o{ RACE_ROUTE_POINTS : route
    RACCE_CATEGORIES ||--o{ PARTICIPANTS : categorizes
    PARTICIPANTS ||--o{ GPS_LOCATIONS : emits
    PARTICIPANTS ||--o{ CHECKPOINT_RECORDS : records
    PARTICIPANTS ||--o{ PARTICIPANT_PENALTIES : incurs
    PARTICIPANTS ||--o{ PARTICIPANT_CREDENTIALS : "rfid/qr"
    CHECKPOINTS ||--o{ CHECKPOINT_RECORDS : at
    RACES ||--o{ RACE_RESULTS : snapshots
    PARTICIPANTS ||--o{ RACE_RESULTS : result
    RACES ||--o{ RFID_READERS : has
    RFID_READERS ||--o{ RFID_READ_EVENTS : ingests
    RACES ||--o{ RACE_MEDIA : gallery
    USERS ||--o{ NOTIFICATIONS : receives
    NOTIFICATIONS ||--o{ NOTIFICATION_DELIVERIES : "channel audit"
    USERS ||--|| NOTIFICATION_PREFERENCES : opts
    PARTICIPANTS ||--o{ PAYMENTS : pays
    PAYMENTS ||--o{ PAYMENT_REFUNDS : refunds
    PARTICIPANTS ||--o{ EMERGENCY_ALERTS : raises
    RACES ||--o{ IMPORT_JOBS : imports
    RACES ||--o{ ATHLETE_FOLLOWS : followed
```

Notes:

- `race_results` is the published-results snapshot (`UNIQUE(participant_id, race_id)`); reads
  for published races serve it, otherwise results are computed from checkpoint records.
- `notification_deliveries` records one row per (notification, channel) with status
  `SENT`/`SKIPPED`/`FAILED`.
- `checkpoint_records.is_cutoff_missed` marks late reads; `races.completed_at` records verified
  completion.
- `gps_locations` is the highest-volume table; retention is age-based and partitioning on
  `recorded_at` is planned as an operations migration.
