# Focused security review — OAuth, GDPR, refunds, RFID, notifications

Scope: the security-sensitive surfaces added in the P1 product-depth work. This is an
internal review to de-risk the pilot, not a substitute for an independent audit.

## Verified design properties

- **Session/CSRF model.** Access tokens are accepted **only** via the `Authorization: Bearer`
  header (`JwtAuthenticationFilter`); they are never read from cookies. Browser sessions keep
  the refresh token in an `HttpOnly`, `SameSite=Lax` cookie scoped to
  `/api/v1/auth/browser`, and the `POST /refresh` and `/logout` endpoints additionally require
  a double-submit CSRF token (`rt_browser_csrf`) plus an allow-listed `Origin`. CSRF at the
  filter chain is therefore disabled safely — cross-site requests cannot present a bearer
  token.
- **RFID ingest.** Readers authenticate with `X-RFID-Reader-Key`; only the SHA-256 hash is
  stored, and `(reader_id, source_event_id)` is uniquely indexed for replay/idempotency.
- **Public spectator surface.** `/api/v1/public/races/**` is anonymous and rate-limited, and
  the DTOs are PII-free (no email/phone/emergency/medical/device/audit fields).
- **GDPR erasure.** Self-service erasure anonymizes the user and their participants, deletes
  push devices, and invalidates outstanding refresh tokens.

## Findings addressed

| # | Severity | Finding | Fix |
|---|---|---|---|
| S1 | Medium | Google ID token `iss` was not asserted (audience only checked when a client id was configured). | `GoogleOAuthIdentityVerifier` now rejects any issuer other than `accounts.google.com`. |
| S2 | Medium | Refund was not idempotent under a crash between the provider call and persistence, allowing a double refund. | Refund now records a `PENDING` row **before** the provider call, transitions to `REFUNDED`/`FAILED`, and short-circuits when a `REFUNDED` row already exists (`V31`). |
| S3 | Low | Provider refund failures were unaudited. | Failures persist a `FAILED` row with `error_message`. |
| S4 | Low | GDPR export/erase had no accountability trail. | Both write append-only audit events (`ACCOUNT_DATA_EXPORTED`, `ACCOUNT_ERASED`). |
| S5 | Low | Readiness depended on SMTP reachability (mail health indicator). | Mail health indicator disabled; readiness reflects database/Redis only. |

## Residual risks and recommendations

- **Rate limiting**: `POST /auth/oauth/google` and `POST /api/v1/rfid/read-events` are public
  and unthrottled. Add per-IP/per-reader throttling via `RequestRateLimiter` before pilot.
- **Provider refund endpoint**: `PesepayClient.refund` posts to a configurable path; confirm
  the exact Pesepay reversal contract against the provider's docs before enabling in prod.
- **Tokeninfo dependency**: Google verification relies on the remote tokeninfo endpoint; it
  fails closed on network errors, but consider local JWKS cache validation for resilience.
- **Erase irreversibility**: `DELETE /me` now requires password re-auth + typed confirmation and
  starts a grace period (default 30 days) with a cancel path before the hard erase runs; consider
  a re-auth-confirmation email for production.
