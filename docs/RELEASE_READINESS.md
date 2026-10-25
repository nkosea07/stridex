# Production-Ready Pilot Release Readiness

**Last reviewed:** 2026-10-25
**Target:** 5,000 participants, 10,000 concurrent spectators, 100 RFID reads/second  
**GPS detail retention:** 90 days  
**Production identity:** Verified-email password authentication; OAuth disabled  
**Messaging:** Email and push only; SMS and WhatsApp disabled

This document is the release gate for the AMS production-ready pilot. A capability is not
considered production-ready until its implementation, automated verification, runtime
verification, and operational ownership are all evidenced separately.

## Status legend

- **Done:** Implemented and verified at the stated evidence level.
- **Partial:** Implemented in code, but at least one required proof level remains open.
- **Pending:** Work remains in the repository.
- **Blocked:** An external decision, credential, provider, or environment is required.

## Release gates

| Gate | Current status | Required evidence | Owner |
|---|---|---|---|
| Race-scoped authorization | Partial | Cross-race controller and WebSocket tests | Backend |
| Legacy race-admin remediation | Partial | Super-admin assignment endpoint, audit evidence, and operator procedure | Backend/Ops |
| API error safety | Partial | Stable error codes, request IDs, no internal messages | Backend |
| PostGIS/Flyway V1–V33 | Partial | Clean migration and upgrade smoke on PostGIS | Platform |
| GPS retention/partitioning | Pending | Monthly partition migration, purge, restore test | Platform/DBA |
| Redis rate limiting | Partial | Production Redis enforcement and alert evidence | Platform |
| Contract consistency | Partial | Controller/client/contract validator passes | Backend/Web/Mobile |
| Web quality | Partial | Browser-backed smoke, accessibility, bundle evidence | Web |
| Mobile quality | Partial | iOS/Android device smoke and push evidence | Mobile |
| RFID hardware | Partial | Physical reader ingest and recovery evidence | RFID/Ops |
| Media storage | Partial | AWS S3 and MinIO provider smoke | Backend/Platform |
| Backups and restore | Pending | Restore drill meeting RPO 15m/RTO 1h | Platform/DBA |
| Load capacity | Pending | k6 report meeting pilot targets | Platform |
| Documentation/runbooks | Partial | Current procedures and ownership confirmed | Product/Ops |
| Refund reconciliation | Partial | V35 PostGIS upgrade, provider-receipt review, legacy pending-row reconciliation | Finance/Ops |
| Account erasure | Partial | V36 PostGIS upgrade, storage-failure alert, object deletion and restore drill, confirmed completion-notice delivery | Privacy/Ops |
| Deployment input validation | Partial | Render tests passed; production mail and storage secrets, staging/prod manifest and startup evidence required | Platform |

## Explicitly deferred for this release

- SMS provider integration.
- WhatsApp provider integration.
- Apple OAuth.
- Facial media matching.
- Advanced AI forecasting and fraud detection.

These features must remain disabled or clearly labelled as unavailable. Their interfaces may
remain as future extension points, but production configuration must not imply that they are
operational.

## Evidence commands

```bash
pnpm contracts:validate
pnpm i18n:check
pnpm --filter web lint
pnpm --filter web typecheck
pnpm --filter web test
pnpm --filter web build
pnpm --dir apps/mobile lint
pnpm --dir apps/mobile exec tsc --noEmit
pnpm --dir apps/mobile test
pnpm --filter rfid-gateway test
```

```bash
cd apps/backend && mvn -B test
cd apps/rfid-reader-demo && mvn -B test
cd apps/rfid-reader-impinj && mvn -B test
```

The commands above provide source and automated-test evidence only. They do not prove
provider delivery, physical hardware behavior, deployed Redis enforcement, backups, or
production traffic capacity.

## Go/no-go rule

The pilot is **no-go** while any high-risk gate is Partial, Pending, or Blocked without an
explicit written risk acceptance from the technical owner and product owner.
