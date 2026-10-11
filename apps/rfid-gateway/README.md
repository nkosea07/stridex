# RFID Gateway

Local edge gateway for checkpoint timing readers. It accepts normalized RFID reads over local HTTP, persists them to a durable spool directory, forwards them to the backend RFID ingest API, and sends periodic reader heartbeats with backlog and hardware metadata.

## Environment

- `RFID_BACKEND_BASE_URL` required, e.g. `http://localhost:8081`
- `RFID_BACKEND_READER_KEY` required, one-time key created from `POST /api/v1/races/{raceId}/rfid/readers`
- `RFID_GATEWAY_PORT` default `8090`
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

## Endpoints

- `POST /reads` accepts `{ "reads": [...] }` using the same normalized read shape as the backend
- `POST /flush` triggers a manual spool flush
- `POST /heartbeat` triggers an immediate heartbeat
- `GET /health` returns backlog and last error state

## Run

```bash
pnpm --filter rfid-gateway start
```

## Edge Profiles

Use the same gateway for both reader setups and distinguish them through heartbeat metadata:

- Simulation mode stores reads with `eventOrigin=SIMULATED` and is safe for local/pilot rehearsals.
- Hardware mode stores reads with `eventOrigin=HARDWARE` and requires an explicit reader serial and hardware profile.

- Demo profile:
  - `RFID_GATEWAY_HARDWARE_PROFILE=DEMO_COMPACT_UHF`
  - `RFID_GATEWAY_READER_CLASS=FIXED_COMPACT_2_PORT`
- Production profile:
  - `RFID_GATEWAY_HARDWARE_PROFILE=IMPINJ_EVENT_GRADE`
  - `RFID_GATEWAY_READER_CLASS=FIXED_4_PORT`
