# Impinj Reader Adapter

Standalone edge adapter skeleton for controlling Impinj readers and forwarding normalized reads into the local `rfid-gateway`.

## What It Does

- Starts a pluggable reader source
- Normalizes tag reads into the platform RFID event shape
- Buffers reads locally in memory
- Forwards batches to the local gateway `POST /reads`
- Exposes `GET /status` and actuator health for edge diagnostics

## Current Modes

- `mock`: default, emits synthetic reads for local testing
- `octane`: placeholder seam for the real Impinj Octane SDK implementation

## Environment

- `ADAPTER_PORT` default `8091`
- `RFID_GATEWAY_BASE_URL` default `http://localhost:8090`
- `IMPINJ_READER_MODE` default `mock`
- `IMPINJ_READER_HOST` default `127.0.0.1`
- `IMPINJ_READER_PORT` default `5084`
- `IMPINJ_READER_NAME` default `impinj-r700`
- `IMPINJ_SDK_DIR` optional directory containing Octane SDK jars when they are not already on the classpath
- `IMPINJ_ENABLED_ANTENNAS` default `1,2,3,4`
- `IMPINJ_TX_POWER_DBM` optional antenna transmit power
- `IMPINJ_RX_SENSITIVITY_DBM` optional receive sensitivity override
- `IMPINJ_SESSION` default `2`
- `IMPINJ_SEARCH_MODE` default `DualTarget`
- `IMPINJ_KEEPALIVE_INTERVAL_MS` default `5000`

## Run

```bash
cd apps/rfid-reader-impinj
mvn spring-boot:run
```

## Edge Profile

Use this adapter with the gateway configured for:

- `RFID_GATEWAY_HARDWARE_PROFILE=IMPINJ_EVENT_GRADE`
- `RFID_GATEWAY_READER_CLASS=FIXED_4_PORT`

## Next Step

Provide the Impinj Octane SDK jars and switch `IMPINJ_READER_MODE=octane` for real hardware control. See [docs/rfid-race-pilot-runbook.md](/Users/nkosinxumalo/WebProjects/AMS/docs/rfid-race-pilot-runbook.md).
