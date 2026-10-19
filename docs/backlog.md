# Backlog

Product and engineering work items not yet scheduled. Each item lists the goal, the current
state, the required scope, and acceptance criteria. Decisions that block work live in
`open-decisions.md`. For a single consolidated status of all pending work, see
`docs/PENDING_WORK.md`.

---

## B1. Self-service account deletion (full user-data erasure)

**Status: delivered (2026-10-11).** Re-auth (password) + typed `DELETE` confirmation, a 30-day
grace window (`racetracker.privacy.deletion-grace-days`), cancel, scheduled hard erase
(`AccountDeletionServiceImpl`), and expanded coverage (credentials, GPS, notifications,
preferences, follows, tokens, media, de-identified emergency alerts; payments/audit retained).
Endpoints `DELETE /me`, `GET /me/deletion`, `POST /me/deletion/cancel`; web Settings dialog and
mobile deletion screen. Confirmation email on request and a completion email after hard erase
are sent (best-effort; skipped when mail is disabled).

**Goal.** Give a user an option to delete their account and have **all data related to that
user** erased — or anonymized where a legal/financial retention obligation applies — following
GDPR best practice.

**Historical pre-delivery baseline (superseded by the delivered status above).** Before the
2026-10-11 implementation, `DELETE /api/v1/me` → `AccountPrivacyServiceImpl.eraseAccount`
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

---

## B2. Privacy policy page (web + mobile, referenced across both apps)

**Status: delivered (2026-10-11).** Contract `legal` block (version + path), web `/privacy`
page, mobile `privacy` screen, links from register (with acknowledgement) and settings, and the
accepted version recorded at registration (`V32`). Client i18n still tracked in
`open-decisions.md` #6.

**Goal.** Publish a single, canonical privacy policy that is reachable from both the web and
mobile apps, and reference it wherever personal data is collected or managed.

**Historical pre-delivery baseline (superseded by the delivered status above).** Before the
2026-10-11 implementation, no privacy policy page or route existed; registration/waiver flows
and the account/privacy settings did not link to one.

**Scope.**

1. **Canonical content + source of truth.** Author the policy (data collected, purposes, legal
   basis, retention — tied to the B1 exceptions, sharing/sub-processors, user rights including
   export/erasure, contact). Keep it versioned (e.g. `privacyVersion`) so consent can reference
   a specific revision. Serve from one maintained source so web and mobile stay in sync.
2. **Web.** Add a public `/privacy` route (React Router 7) that is anonymous and PII-free,
   reachable without auth; link from the footer, sign-up, and Privacy/Settings.
3. **Mobile.** Add a `privacy` screen (Expo Router) with the same content; link it from
   registration and the profile → Privacy entry; open externally if content is hosted.
4. **References.** Link the policy from: registration (with an "I agree" acknowledgement),
   the profile/privacy settings, the account-deletion flow (B1), and the waiver acceptance.
5. **Caching/updates.** Cache-bust on version change; show the "last updated" date and the
   version; prompt re-acknowledgement when the version changes.
6. **Accessibility/i18n.** Accessible headings and contrast; ready for the client-i18n work
   (`open-decisions.md` #6).

**Acceptance criteria.**
- `/privacy` on web renders anonymously and is linked from sign-up and privacy settings.
- A mobile privacy screen exists and is linked from registration and profile.
- The policy version is stored and surfaced; the registration acknowledgement records the
  accepted version.
- No PII is transmitted or logged to render the page.

**Related.** B1 (account deletion), `open-decisions.md` #6 (client i18n), contract if the
version is exposed via the API.
