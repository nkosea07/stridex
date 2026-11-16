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
| PostGIS/Flyway V1–V58 | Partial | Clean V1→V58 and V36→V58 upgrade smoke on PostGIS | Platform |
| GPS retention/partitioning | Pending | Monthly partition migration, purge, restore test | Platform/DBA |
| Redis rate limiting | Partial | Production Redis enforcement and alert evidence | Platform |
| Contract consistency | Partial | Controller/client/contract validator passes | Backend/Web/Mobile |
| Web quality | Partial | Browser-backed smoke, accessibility, bundle evidence | Web |
| Mobile quality | Partial | iOS/Android device smoke and push evidence | Mobile |
| RFID hardware | Partial | Physical reader ingest and recovery evidence; Octane first-seen timestamps verified on a real R700 (`timestampSource=READER` in raw payloads); reader and gateway NTP offset under 2 s; gateway token configured; dedupe window tuned for the mat layout; pre-gun, earlier-read, and unmatched-tag reprocess drills recorded | RFID/Ops |
| Media storage | Partial | AWS S3 and MinIO provider smoke | Backend/Platform |
| Backups and restore | Pending | `scripts/backup-restore-drill.sh` evidence from production-sized data meeting RPO 15m/RTO 1h, followed by a backend start against the restored copy | Platform/DBA |
| Load capacity | Pending | k6 report meeting pilot targets | Platform |
| Documentation/runbooks | Partial | Current procedures and ownership confirmed | Product/Ops |
| Refund reconciliation | Partial | Attempt-bound reconciliation, release-gate SQL, finance review view, provider-receipt evidence, and zero legacy partial pending rows | Finance/Ops |
| Account erasure | Partial | Full PostGIS migration chain through V58, per-account failure isolation, audited unknown-scope remediation, storage-failure alert, object deletion and restore drill, confirmed completion-notice delivery | Privacy/Ops |
| Participant media consent | Partial | Consent grant/revoke, anonymous listing/original/thumbnail denial, admin visibility, attestation/revocation audit evidence | Privacy/Web |
| WhatsApp notifications | Partial | Meta Cloud API template send, signed webhook, delivery-status audit, staging delivery evidence | Backend/Ops |
| Registration and race depth | Partial | Auto-bib/athlete code, medical fields, QR check-in, stages, sponsors, timezone, early-bird/team pricing | Backend/Web/Ops |
| Results depth | Partial | Personal bests, qualification status, cross-race history, configurable medal places | Backend/Web |
| Mobile operations and PWA | Partial | Mobile timing/incident mode, PWA shell, device smoke evidence, offline QR queue | Mobile/Web |
| Deterministic insights | Partial | Dropout-risk and anomaly foundation; forecasting, injury, pacing, crowd, and LLM features remain deferred | Backend/Product |
| Emergency SOS delivery | Partial | Durable dispatcher, live ops panel, fresh/stale/missing GPS behavior, channel-failure retry, zero-recipient escalation drill, and on-call ownership | Backend/Ops |
| Deployment input validation | Partial | Render and fail-closed preflight tests pass; production mail/storage secrets, staging/prod manifest, and startup evidence required | Platform |
| Result integrity | Partial | Publish → review anomalies and queries → certify drill on a pilot race; integrity check shows intact chain; reopen-and-republish drill recorded | Timing/Ops |
| Admin two-factor sign-in | Partial | Every super admin and race admin enrolled; recovery codes stored; MFA reset procedure rehearsed; `MFA_ENCRYPTION_KEY` held in the secret manager | Security/Ops |
| Security scanning | Partial | Trivy image, dependency, and secret scans plus `pnpm audit` green in CI; Dependabot enabled; external penetration test completed and findings resolved | Security |
| Legal documents | Pending | `operatorName`, `operatorAddress`, and `contactEmail` placeholders in `packages/contracts/contract.json` replaced with the operator's real details; Privacy Policy and Terms reviewed by Zimbabwean counsel against the Cyber and Data Protection Act [Chapter 12:07]; POTRAZ data-controller licensing confirmed; stated GPS retention and deletion grace periods match deployed `TRACKING_RETENTION_DAYS` and `PRIVACY_DELETION_GRACE_DAYS` | Product/Legal |
| Pre-deployment data gates | Partial | `scripts/release-gate.sql` passes in CI/staging with zero blocked refund or media-scope rows | Finance/Privacy/Ops |

## Explicitly deferred for this release

- SMS provider integration.
- WhatsApp provider integration.
- Apple OAuth.
- Facial media matching.
- Advanced AI forecasting and fraud detection.

These features must remain disabled or clearly labelled as unavailable. Their interfaces may
remain as future extension points, but production configuration must not imply that they are
operational.

## Dependency audit exceptions

CI runs `pnpm audit --prod --audit-level high`. Transitive fixes are pnpm `overrides` in `pnpm-workspace.yaml`, kept within each package's current major version. Three advisories have no compatible patched release and are listed in `auditConfig.ignoreGhsas` and, for the Trivy scans, in `.trivyignore` with a 2027-05-16 expiry; revisit them whenever Expo, Metro, or React Navigation is upgraded and remove the entry once the dependency ships a fixed version.

| Advisory | Package | Why it is accepted |
| --- | --- | --- |
| GHSA-5p2g-fcmc-qvqq, GHSA-w3rx-r6r6-pgpr (CVE-2025-71329, CVE-2025-71330) | `image-size` 1.x via Metro | Build-time only: Metro reads the app's own image assets while bundling. The fix is in 2.x, whose main export no longer accepts the file path Metro passes. |
| GHSA-qr28-fpjm-wq7f (CVE-2026-118664) | `decode-uri-component` 0.2.x via React Navigation's `query-string` 7 | The fix (0.5.x) is ESM-only and `query-string` 7 loads it with `require()`. Impact is a slow parse of a crafted link inside the user's own app. |

Install with a release-age guard when changing dependencies: `pnpm install --config.minimum-release-age=20160` (14 days).

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
