# OAuth and RFID Rate-Limiting Design

**Date:** 2026-10-12  
**Status:** Implemented; local verification complete

## Goal

Close the documented public-endpoint rate-limiting gap for Google OAuth and RFID read
ingest without introducing a new persistence layer or changing the API response contract.

## Design

The existing `RequestRateLimiter` remains the single enforcement point. It already uses an
atomic Redis script, hashes bucket identities, fails closed when Redis is unavailable, and
emits Micrometer counters. Two policies are added:

- OAuth: 10 requests per client IP per 15 minutes.
- RFID read ingest: 120 requests per client IP per minute and 600 requests per supplied
  reader key per minute. The reader key is hashed before it becomes a Redis key.

The OAuth controller checks the client address before token verification. The RFID ingest
controller checks both identities before handing the batch to the ingest service. RFID
heartbeat remains unthrottled because it is a health signal and is not the documented
ingest abuse path.

RFID client-address resolution uses the existing trusted-proxy-aware
`ClientAddressResolver`; the controller will no longer trust an arbitrary
`X-Forwarded-For` header.

## Documentation scope

- Mark the 2026-05-31 and 2026-10-09 gap analyses as historical and point readers to the
  2026-10-12 pending-work register.
- Correct the delivered B1/B2 backlog entries whose old pre-delivery paragraphs still say
  the features are partial or absent.
- Update the backend README's stale “later phases” wording while retaining genuinely
  deferred vendor-adapter and CSV timing-import work.

## Verification

- Unit-test limiter bucket count, hashed identities, limits, blocked responses, and Redis
  fail-closed behavior.
- Add OAuth controller coverage for client-IP rate-limit invocation.
- Add RFID controller coverage for rate-limit invocation, including the reader key and
  trusted client address.
- Run focused backend tests, full backend Maven tests, and the repository's documented
  contract, web, mobile, gateway, reader-adapter, and release evidence commands.
