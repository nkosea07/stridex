# RFID Race Pilot Runbook

This runbook covers the first production RFID pilot using the shared backend, `rfid-gateway`, the compact demo bridge, and the Impinj production adapter.

## 1. Backend Preparation

Apply the RFID scale migration before registering readers:

```bash
cd apps/backend
psql "$DATABASE_URL" -f database/migrations/2026-06-02-rfid-scale-foundation.sql
```

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

### Production Impinj profile

```bash
export RFID_BACKEND_BASE_URL=http://localhost:8080/api
export RFID_BACKEND_READER_KEY=<reader-api-key>
export RFID_GATEWAY_NAME=finish-gateway-1
export RFID_GATEWAY_HARDWARE_MODEL=Impinj-R700
export RFID_GATEWAY_HARDWARE_PROFILE=IMPINJ_EVENT_GRADE
export RFID_GATEWAY_READER_CLASS=FIXED_4_PORT
export RFID_GATEWAY_RETRY_INITIAL_DELAY_MS=1000
export RFID_GATEWAY_RETRY_MAX_DELAY_MS=10000
pnpm --filter rfid-gateway start
```

### Demo bridge profile

```bash
export RFID_BACKEND_BASE_URL=http://localhost:8080/api
export RFID_BACKEND_READER_KEY=<reader-api-key>
export RFID_GATEWAY_NAME=demo-gateway-1
export RFID_GATEWAY_HARDWARE_MODEL=compact-fixed-uhf
export RFID_GATEWAY_HARDWARE_PROFILE=DEMO_COMPACT_UHF
export RFID_GATEWAY_READER_CLASS=FIXED_COMPACT_2_PORT
pnpm --filter rfid-gateway start
```

Gateway health endpoint:

- `GET /health`

Interpretation:
- `pendingReads > 0` for a sustained period means backend delivery lag or reader overrun.
- `flushFailureCount > 0` means backend read delivery is failing.
- `heartbeatFailureCount > 0` means reader health is not reaching the backend even if local reads are still being queued.

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

## 6. Race-Day Validation Checklist

1. Register at least one production reader and one demo-profile reader on separate races.
2. Confirm both appear on `/admin/races/{raceId}/rfid`.
3. Confirm reader heartbeats update `lastSeenAt`, `gatewayStatus`, and `gatewayBacklog`.
4. Scan a known participant tag and verify:
   - raw event accepted
   - backend worker normalizes the event
   - `checkpoint_records.recordMethod=RFID`
5. Replay the same tag or source event and verify duplicate handling.
6. Submit an unknown tag and verify it appears as unmatched without producing an official checkpoint record.
7. Interrupt gateway/backend connectivity and verify queued reads survive restart and are delivered later.

## 7. Failure Interpretation

- `gatewayStatus=OFFLINE` and stale `lastHeartbeatAt`: edge service is down or disconnected.
- `gatewayStatus=DEGRADED` with rising backlog: transport path is alive but not keeping up.
- frequent duplicate rate spikes: likely read-zone overlap or repeated reader replay.
- unknown credential rate spikes: participant credential assignment gap or wrong race-scoped reader usage.
