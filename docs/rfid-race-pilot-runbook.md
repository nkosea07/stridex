# RFID Race Pilot Runbook

This runbook covers the first production RFID pilot using the shared backend, `rfid-gateway`, the compact demo bridge, and the Impinj production adapter.

## 1. Backend Preparation

The RFID schema (readers, raw read events, participant credentials, idempotency index) is
created by the backend's Flyway migrations when it starts. Do not apply the legacy scripts in
`apps/backend/database/migrations/` by hand; Flyway (`src/main/resources/db/migration`) is the
only schema source. Confirm the backend started cleanly on the current migration head before
registering readers.

Required platform behavior:
- participant timing identity resolves through `participant_credentials`
- official checkpoint records are created only from RFID/manual records
- reader heartbeat metadata is visible through the race RFID overview and reader list endpoints

## 2. Reader Registration

For each physical checkpoint reader:

1. Create or confirm the race and checkpoint.
2. Ensure the checkpoint has `hasRfidReader=true`.
3. Register the reader:

```http
POST /api/v1/races/{raceId}/rfid/readers
Authorization: Bearer <operator-token>
Content-Type: application/json

{
  "checkpointId": "<checkpoint-id>",
  "name": "Finish Line Reader",
  "externalId": "finish-r700-1"
}
```

Persist the returned plaintext `apiKey` in the local edge environment. The backend stores only the hash.

## 3. Gateway Configuration

The gateway remains the shared transport layer for both demo and production.

`RFID_BACKEND_BASE_URL` is the backend origin only (scheme, host, port). The gateway appends
`/api/v1/rfid/...` itself, so do not include `/api` in the value.

The production backend rejects `SIMULATED` reads (`RFID_SIMULATION_ENABLED=false`). Run the
gateway with `RFID_GATEWAY_MODE=hardware` and real reader serial/profile/model values against
a pilot or production backend; simulation mode is for a local development backend only.

### Production Impinj profile

```bash
export RFID_BACKEND_BASE_URL=http://localhost:8081
export RFID_BACKEND_READER_KEY=<reader-api-key>
export RFID_GATEWAY_NAME=finish-gateway-1
export RFID_GATEWAY_HARDWARE_MODEL=Impinj-R700
export RFID_GATEWAY_HARDWARE_PROFILE=IMPINJ_EVENT_GRADE
export RFID_GATEWAY_READER_CLASS=FIXED_4_PORT
export RFID_GATEWAY_MODE=hardware
export RFID_GATEWAY_READER_SERIAL=<reader-serial>
export RFID_GATEWAY_RETRY_INITIAL_DELAY_MS=1000
export RFID_GATEWAY_RETRY_MAX_DELAY_MS=10000
pnpm --filter rfid-gateway start
```

### Demo bridge profile

```bash
export RFID_BACKEND_BASE_URL=http://localhost:8081
export RFID_BACKEND_READER_KEY=<reader-api-key>
export RFID_GATEWAY_NAME=demo-gateway-1
export RFID_GATEWAY_HARDWARE_MODEL=compact-fixed-uhf
export RFID_GATEWAY_HARDWARE_PROFILE=DEMO_COMPACT_UHF
export RFID_GATEWAY_READER_CLASS=FIXED_COMPACT_2_PORT
export RFID_GATEWAY_MODE=hardware
export RFID_GATEWAY_READER_SERIAL=<reader-serial>
pnpm --filter rfid-gateway start
```

Edge security and durability:

- The gateway listens on `127.0.0.1` unless `RFID_GATEWAY_HOST` says otherwise, and then requires `RFID_GATEWAY_LOCAL_TOKEN`. Set the same value as `RFID_GATEWAY_LOCAL_TOKEN` for the reader adapter. Never expose the gateway port on venue Wi-Fi without the token: anyone who can post to `/reads` can create finish times.
- Run the gateway host and every reader on NTP. The backend measures each gateway's clock from its heartbeats (`clockOffsetMs` on the reader list) and marks the reader `DEGRADED` beyond 2 s. The Impinj adapter uses the reader's own timestamp and falls back to the host clock, with a warning, when the reader clock is more than `IMPINJ_MAX_READER_CLOCK_OFFSET_MS` (2000) off.
- Keep `raw/` from the gateway spool volume after every race.

Gateway health endpoint:

- `GET /health`

Interpretation:
- `pendingReads > 0` for a sustained period means backend delivery lag or reader overrun.
- `flushFailureCount > 0` means backend read delivery is failing.
- `heartbeatFailureCount > 0` means reader health is not reaching the backend even if local reads are still being queued.
- `failedFiles > 0` means the backend permanently rejected one or more batches (HTTP 4xx other than
  auth/rate-limit, for example simulated reads against a production backend). Those batches are kept
  in the `failed/` spool directory so later reads keep flowing. Read `lastFlushError`, fix the cause,
  then call `POST /failed/requeue` to move them back to the pending queue. Never delete spool files
  during a race.

## 4. Impinj Adapter Configuration

Provide the Octane SDK jars either on the runtime classpath or through `IMPINJ_SDK_DIR`.

```bash
export RFID_GATEWAY_BASE_URL=http://localhost:8090
export IMPINJ_READER_MODE=octane
export IMPINJ_READER_HOST=192.168.10.25
export IMPINJ_READER_PORT=5084
export IMPINJ_READER_NAME=finish-r700-1
export IMPINJ_SDK_DIR=/opt/impinj-octane
export IMPINJ_ENABLED_ANTENNAS=1,2,3,4
export IMPINJ_TX_POWER_DBM=30
export IMPINJ_SESSION=2
export IMPINJ_SEARCH_MODE=DualTarget
export IMPINJ_KEEPALIVE_INTERVAL_MS=5000
export IMPINJ_CONNECT_RETRY_INITIAL_DELAY_MS=1000
export IMPINJ_CONNECT_RETRY_MAX_DELAY_MS=15000
cd apps/rfid-reader-impinj
mvn spring-boot:run
```

Adapter status endpoint:

- `GET /status`

Key fields:
- `readerState`
- `lastReaderError`
- `lastReadAt`
- `lastKeepaliveAt`
- `lastGatewaySuccessAt`
- `consecutiveGatewayFailures`
- `nextGatewayRetryAt`

## 5. Demo Bridge Operation

The demo adapter remains bridge-based in this phase. A vendor utility or compact-reader bridge posts reads to:

```http
POST /reader-events
Content-Type: application/json

{
  "reads": [
    {
      "sourceEventId": "demo-1",
      "rfidTag": "E2000016591702081890B31D",
      "readAt": "2026-06-09T10:00:00Z",
      "antenna": "A1",
      "rssi": -48.2
    }
  ]
}
```

## 6. Timing Rules

- Raw reads are immutable. Results come from checkpoint records derived from them, and every derivation can be re-run.
- **Gun time.** Starting the race (status `IN_PROGRESS`) records the gun time. Reads before it, or before the scheduled start when no gun time exists, are stored as `IGNORED` and never become times. This covers tag tests at packet pickup, warm-ups through the finish chute, and athletes waiting on the start mat. If the race was started late or early in the system, correct the gun time on the RFID page, then reprocess **Ignored reads**.
- **First read wins.** Each checkpoint keeps the earliest read of the athlete's pass. A read processed late (backup reader, delayed spool) that is earlier replaces a later automatic time, and cutoffs and DNF status are re-applied. Official corrections and manual times are never overwritten.
- **Clock faults.** A read dated more than 5 s after the backend received it is `IGNORED` as a reader clock fault.
- **Unmatched tags.** The RFID page lists tags with no assigned participant. Assign the tag (any letter case or separators match), then reprocess **Unmatched tags**. Reprocess **Failed reads** after fixing a registration status or processing error.
- **Live tracking.** RFID crossings update the staff live map immediately. Athletes without the app appear at their last timed checkpoint, and distance remaining, pace, and predicted finish come from the furthest of GPS and timed checkpoints.

## 7. Race-Day Validation Checklist

1. Register at least one production reader and one demo-profile reader on separate races.
2. Confirm both appear on `/admin/races/{raceId}/rfid`.
3. Confirm reader heartbeats update `lastSeenAt`, `gatewayStatus`, and `gatewayBacklog`.
4. Scan a known participant tag and verify:
   - raw event accepted
   - backend worker normalizes the event
   - `checkpoint_records.recordMethod=RFID`
5. Replay the same tag or source event and verify duplicate handling.
5a. Read a tag before starting the race and verify it is `IGNORED`; start the race, read it again, and verify a time is created.
5b. Deliver an earlier read for an athlete who already has a time and verify the time moves earlier.
6. Submit an unknown tag and verify it appears as unmatched without producing an official checkpoint record.
7. Interrupt gateway/backend connectivity and verify queued reads survive restart and are delivered later.

## 8. Failure Interpretation

- `gatewayStatus=OFFLINE` and stale `lastHeartbeatAt`: edge service is down or disconnected.
- `gatewayStatus=DEGRADED` with rising backlog: transport path is alive but not keeping up.
- frequent duplicate rate spikes: likely read-zone overlap or repeated reader replay.
- unknown credential rate spikes: participant credential assignment gap or wrong race-scoped reader usage.
- read events in `ERROR` with "Processing failed after N attempts": the backend could not normalize the
  read (it retried with backoff, about 5 s doubling to 5 min, up to 8 attempts). Fix the cause, then
  review those events; they are not retried automatically.
- read events in `IGNORED` with "before the race start": pre-gun reads, expected in small numbers. A large count after the gun means the gun time is wrong; correct it and reprocess.
- read events in `IGNORED` mentioning the clock: the reader or gateway clock is ahead of the server. Fix NTP, then reprocess ignored reads.
- read events in `ERROR` with "Participant is not an accepted entrant": the tag belongs to a waitlisted,
  pending-approval, or rejected registration, so no checkpoint record was created.
