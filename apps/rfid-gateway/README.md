# RFID Gateway

Local edge gateway for checkpoint timing readers. It accepts normalized RFID reads over local HTTP, persists them to a durable spool directory, forwards them to the backend RFID ingest API, and sends periodic reader heartbeats with backlog and hardware metadata.

## Environment

- `RFID_BACKEND_BASE_URL` required, backend origin only, e.g. `http://localhost:8081` (the gateway appends `/api/v1/rfid/...`)
- `RFID_BACKEND_READER_KEY` required, one-time key created from `POST /api/v1/races/{raceId}/rfid/readers`
- `RFID_GATEWAY_PORT` default `8090`
- `RFID_GATEWAY_HOST` default `127.0.0.1`. Any non-loopback value (the Docker image uses `0.0.0.0`) requires `RFID_GATEWAY_LOCAL_TOKEN`
- `RFID_GATEWAY_LOCAL_TOKEN` shared secret (24+ characters) that adapters send as `X-Gateway-Token`; required for every endpoint except `GET /health` when set
- `RFID_GATEWAY_DEDUPE_WINDOW_MS` default `3000`; repeated reads of one tag closer together than this are one pass and only the first is forwarded (`0` disables)
- `RFID_GATEWAY_SPOOL_DIR` default `./data/rfid-gateway`
- `RFID_GATEWAY_NAME` default `edge-gateway`
- `RFID_GATEWAY_FIRMWARE_VERSION` default `dev`
- `RFID_GATEWAY_HARDWARE_MODEL` default `generic-edge`
- `RFID_GATEWAY_HARDWARE_PROFILE` default `UNSPECIFIED`
- `RFID_GATEWAY_READER_CLASS` default `UNKNOWN`
- `RFID_GATEWAY_MODE` default `simulation`; accepted values: `simulation`, `hardware`
- `RFID_GATEWAY_READER_SERIAL` required in `hardware` mode
- `RFID_GATEWAY_RETRY_INITIAL_DELAY_MS` default `1000`
- `RFID_GATEWAY_RETRY_MAX_DELAY_MS` default `10000`
- `RFID_GATEWAY_BATCH_SIZE` default `200`; must be 1–200 (the backend accepts at most 200 reads per request)

## Endpoints

- `POST /reads` accepts `{ "reads": [...] }` using the same normalized read shape as the backend
- `POST /flush` triggers a manual spool flush
- `POST /heartbeat` triggers an immediate heartbeat
- `POST /failed/requeue` moves batches from the `failed/` spool back to `pending/`
- `GET /health` returns backlog, failed-spool count, and the last flush/heartbeat errors

## Delivery semantics

- Reads are spooled to `pending/`, leased into `processing/` while a request is in flight, and removed only after the backend accepts the batch. Network errors and timeouts return the batch to `pending/` with exponential backoff.
- HTTP 401/403/408/409/425/429 and 5xx responses are retried with backoff. A revoked reader key is also retried, so reads are retained until a new key is configured.
- Any other 4xx means the backend will never accept that batch as sent. It moves to `failed/` so later reads keep flowing; inspect `lastFlushError`, fix the cause, then `POST /failed/requeue`.
- `POST /reads` validates each read before spooling: `rfidTag` is required, `readAt` must be ISO-8601 with a UTC offset, and `rssi` must be numeric when present.
- Tags are canonicalised (hex EPCs upper-case, separators removed) exactly as the backend does. Reads without a `sourceEventId` get a stable one derived from gateway, reader serial, tag, read time, and antenna, so a retried batch is recognised as the same reads.
- `eventOrigin` always comes from `RFID_GATEWAY_MODE`; a value sent by the caller is ignored.
- Every accepted read is appended to `raw/reads-YYYY-MM-DD.ndjson` (fsynced) before deduplication. Keep this file after the race; it is the edge record for timing disputes. The gateway never prunes it.
- Spool files are written to a temporary file, fsynced, and renamed, so a power cut cannot leave a half-written batch. A file that is unreadable anyway is moved to `corrupt/` (`corruptFiles` in `/health`) instead of blocking later batches.

## Run

```bash
pnpm --filter rfid-gateway start
```

## Edge Profiles

Use the same gateway for both reader setups and distinguish them through heartbeat metadata:

- Simulation mode stores reads with `eventOrigin=SIMULATED`. Only a development backend with `RFID_SIMULATION_ENABLED=true` accepts them; pilot and production backends reject them.
- Hardware mode stores reads with `eventOrigin=HARDWARE` and requires an explicit reader serial and hardware profile.

- Demo profile:
  - `RFID_GATEWAY_HARDWARE_PROFILE=DEMO_COMPACT_UHF`
  - `RFID_GATEWAY_READER_CLASS=FIXED_COMPACT_2_PORT`
- Production profile:
  - `RFID_GATEWAY_HARDWARE_PROFILE=IMPINJ_EVENT_GRADE`
  - `RFID_GATEWAY_READER_CLASS=FIXED_4_PORT`
