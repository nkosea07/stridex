# P0 Commercialization Implementation (2026-10-09)

Implements the P0 scope from `docs/GAP_ANALYSIS_2026_10_09.md`. Scope decisions:
**Pesepay** (covers EcoCash/cards/Zimswitch), **Firebase FCM direct**, **backend + web**
(mobile deferred), **public signed-token certificates**, **CSV + PDF + XLSX** exports.

## Migrations (append-only)

| Version | Purpose |
|---|---|
| `V14__race_clone_archive.sql` | `races.archived_at` + index |
| `V15__timing_corrections.sql` | `participant_penalties`; `checkpoint_records.adjusted_recorded_at/reason/by/at` |
| `V16__push_devices.sql` | `push_devices` (FCM tokens per user) |
| `V17__payments.sql` | `payments` (Pesepay transactions, idempotent `provider_ref`) |

## P0-1 · Race clone & archive

- `POST /races/{id}/clone` deep-copies categories, checkpoints, registration fields into a
  new `DRAFT` race. `POST /races/{id}/archive` / `/restore` toggle `archived_at`.
- Default listings exclude archived races (`?includeArchived=true` to show them).
- Web: `RacesPage` clone dialog + archive/restore + show-archived toggle.

## P0-2 · Checkpoint penalties & time corrections

- `participant_penalties` seconds are added to finish time; `checkpoint_records` support an
  official adjusted time while preserving the raw `recorded_at`.
- Result computation and the native ranking query both honor adjustments and penalties, so
  positions change accordingly.
- Endpoints: `POST/DELETE /races/{raceId}/participants/{id}/penalties`,
  `PATCH /races/{raceId}/checkpoints/{checkpointId}/records/{recordId}`.
- Web: `ParticipantsPage` add-penalty action.

## P0-3 · Results exports & certificates

- `GET /races/{raceId}/results/export?format=csv|pdf|xlsx` (opencsv, iText, Apache POI).
- `CertificateService` renders an official-finisher PDF from race branding/result.
- `GET /races/{raceId}/results/{participantId}/certificate` (staff) and anonymous
  `GET /public/races/{raceId}/results/{participantId}/certificate?token=` gated by a
  short-lived HMAC-signed `CertificateTokenService` token (PII-safe, non-enumerable).
- Web: `ResultsPage` export dropdown; public results certificate link.

## P0-4 · Push notifications (FCM)

- `push_devices` + `POST/DELETE /notifications/devices`.
- `NotificationService.notify(...)` persists in-app notifications and dispatches FCM via
  `PushNotificationService` (firebase-admin, lazy init, best-effort).
- Wired triggers: registration approved/rejected/waitlisted.
- Web: `enableWebPush()` (Firebase JS SDK, guarded by `VITE_FIREBASE_*` + VAPID) and a
  `SettingsPage` enable control. Mobile registration deferred.

## P0-5 · Payments (Pesepay)

- `PesepayClient` implements Pesepay's AES-256-CBC payload encryption (key = 32-char
  encryption key, IV = first 16 chars) with configurable sandbox/prod base URL.
- `POST /races/{raceId}/participants/{id}/payment/checkout` starts a redirect checkout.
- `POST /payments/callback/pesepay` verifies the `Authorization` header, re-fetches the
  real status server-to-server, and is idempotent on `referenceNumber`; on `SUCCESS` the
  participant is marked `PAID`. Manual `verifyPayment` remains for offline payments.
- Config: `racetracker.payments.*` (`PAYMENTS_ENABLED`, `PESEPAY_BASE_URL`,
  `PESEPAY_INTEGRATION_KEY`, `PESEPAY_ENCRYPTION_KEY`, result/return URLs).

## Configuration

`.env.pilot.example` and `ProductionConfigurationValidator` gained push and payment
guards. New web env: `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_PROJECT_ID`,
`VITE_FIREBASE_MESSAGING_SENDER_ID`, `VITE_FIREBASE_APP_ID`, `VITE_FIREBASE_VAPID_KEY`.

## Contract

New endpoint keys in `packages/contracts` (+ `validate-contracts.mjs` sync): `raceClone`,
`raceArchive`, `raceRestore`, `raceParticipantPenalties`, `raceParticipantPenaltyDelete`,
`checkpointRecordCorrect`, `raceResultsExport`, `raceResultCertificate`,
`publicRaceResultCertificate`, `notificationDevices`, `raceParticipantPaymentCheckout`,
`paymentCallbackPesepay`.

## Verification

`pnpm contracts:validate`; web lint/typecheck/test/build; mobile lint/tsc/test;
`pnpm --filter rfid-gateway test`; backend `mvn -B test` (149 tests).
