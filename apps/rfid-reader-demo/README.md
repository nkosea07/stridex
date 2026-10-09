# Demo Reader Adapter

Standalone compact-UHF edge adapter for demo environments. It accepts local reader events, normalizes them into the platform RFID payload shape, buffers them in memory, and forwards them to the shared `rfid-gateway`.

## What It Does

- Runs a compact-reader profile separate from the production Impinj adapter
- Accepts normalized or near-normalized read batches at `POST /reader-events`
- Buffers reads locally in memory
- Forwards batches to the local gateway `POST /reads`
- Exposes `GET /status` and actuator health for edge diagnostics

## Current Modes

- `bridge`: default, intended for a lightweight vendor bridge or compact reader utility posting real tag reads into `/reader-events`
- `mock`: emits synthetic reads for local development and tests

## Environment

- `ADAPTER_PORT` default `8092`
- `RFID_GATEWAY_BASE_URL` default `http://localhost:8090`
- `DEMO_READER_MODE` default `bridge`
- `DEMO_READER_NAME` default `compact-uhf-demo`
- `DEMO_READER_HARDWARE_MODEL` default `compact-fixed-uhf`
- `DEMO_READER_EXPECTED_ANTENNAS` default `2`

## Run

```bash
cd apps/rfid-reader-demo
mvn spring-boot:run
```

## Edge Profile

Use this adapter with the gateway configured for:

- `RFID_GATEWAY_HARDWARE_PROFILE=DEMO_COMPACT_UHF`
- `RFID_GATEWAY_READER_CLASS=FIXED_COMPACT_2_PORT`

This keeps demo readers visible in the same backend inventory and heartbeat model as event-grade readers while isolating them at the race level.
