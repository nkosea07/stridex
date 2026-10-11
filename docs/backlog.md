# Backlog

Product and engineering work items not yet scheduled. Each item lists the goal, the current
state, the required scope, and acceptance criteria. Decisions that block work live in
`open-decisions.md`.

---

## B1. Self-service account deletion (full user-data erasure)

**Goal.** Give a user an option to delete their account and have **all data related to that
user** erased — or anonymized where a legal/financial retention obligation applies — following
GDPR best practice.

**Current state (partial).** `DELETE /api/v1/me` → `AccountPrivacyServiceImpl.eraseAccount`
already: anonymizes the user and their participants, deletes push devices, invalidates
outstanding refresh tokens, and writes an `ACCOUNT_ERASED` audit event. Missing: confirmation
and re-authentication, a grace/undo window, full coverage of every related table, async handling
for large datasets, and user communication.

**Best-practice design.**

1. **Confirmation & re-auth.** Require the current password (or an MFA code for privileged
   roles) plus an explicit typed confirmation; never delete from a single unauthenticated-shaped
   request. Rate-limit the endpoint.
2. **Soft delete + grace period.** On request: deactivate immediately, set `deleted_at`, and
   start a configurable grace window (e.g. 30 days). Allow the user to cancel within the window;
   a scheduled job performs the **hard** erase when it expires.
3. **Offer export first.** Prompt the user to download their data (`GET /me/data`) before erasing.
4. **Comprehensive data coverage** (audit every table with a user/participant FK):
   - `users` — scrub PII, disable, `deleted_at`.
   - `participants` — anonymize PII; retain timing/results anonymized.
   - `participant_credentials` — delete RFID/QR credentials.
   - `gps_locations` — delete (or detach) personal traces.
   - `notifications`, `notification_deliveries`, `notification_preferences` — delete.
   - `push_devices` — delete.
   - `athlete_follows` — delete.
   - `auth_tokens` (refresh/verification/reset) — invalidate/delete.
   - `race_media` — delete or strip uploader attribution.
   - `emergency_alerts` — anonymize reporter/participant linkage; **retain** the safety record.
   - `payments` / `payment_refunds` — retain for financial/tax law; anonymize the participant
     linkage where the schema allows.
   - audit events — append-only; retain (references the user id for compliance).
5. **Legal retention exceptions.** Document which data is retained (financial records, safety
   incidents, append-only audit) under GDPR Art. 17(3) and reflect it in the privacy notice.
6. **Async + retry.** Erasure of large histories runs on a worker (mirror
   `ParticipantImportProcessor`) with idempotency and retry, so the request returns immediately.
7. **Idempotent & safe.** Repeated requests are no-ops; partial failures are resumable.
8. **Communication.** Send a confirmation email on request, and a completion notice (to the
   original address captured at request time) when the hard erase finishes.

**Acceptance criteria.**
- A test per data category asserting the row is deleted or anonymized after erase.
- Authorization test: a user can delete only their own account; admins/organizers cannot erase
  another user's data through this path.
- The grace-window cancel path and the scheduled hard-erase path both covered by tests.
- No PII remains for the erased user in the covered tables (verified by a query-based test).
- Documented retention exceptions.

**Related.** `docs/security-review.md` (erase irreversibility note), `docs/open-decisions.md`
(rate limiting), existing `DELETE /me`.
