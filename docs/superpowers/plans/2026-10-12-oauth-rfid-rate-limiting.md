# OAuth and RFID Rate-Limiting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Redis-backed throttling to public Google OAuth and RFID read-ingest endpoints, correct stale status documentation, and collect CI/release evidence.

**Architecture:** Extend the existing `RequestRateLimiter` with policy-specific hashed Redis buckets. Controllers invoke the limiter before service calls; RFID uses the existing trusted-proxy-aware `ClientAddressResolver`. Documentation changes label old audits as historical and make current delivered/pending status internally consistent.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring MVC, Redis Lua rate limiter, Micrometer, JUnit 5, Mockito, MockMvc, Markdown, pnpm, Maven.

---

### Task 1: Normalize stale documentation

**Files:**
- Modify: `docs/GAP_ANALYSIS_2026_05_31.md`
- Modify: `docs/GAP_ANALYSIS_2026_10_09.md`
- Modify: `docs/backlog.md`
- Modify: `apps/backend/README.md`

- [x] **Step 1: Add historical-status notices to dated gap analyses**

State that the documents are historical snapshots and that `docs/PENDING_WORK.md` is the current register.

- [x] **Step 2: Correct delivered backlog wording**

Retain the original acceptance/design text, but label the old B1/B2 “Current state” paragraphs as pre-delivery baselines and point to the delivered status.

- [x] **Step 3: Correct backend README status wording**

Describe RFID reader administration as implemented, while retaining vendor SDK adapters and CSV timing imports as deferred work.

- [x] **Step 4: Review the documentation diff**

Run `git diff --check` and inspect only the intended documentation files.

### Task 2: Add rate-limit policies

**Files:**
- Modify: `apps/backend/src/main/java/com/racetracker/security/RequestRateLimiter.java`
- Test: `apps/backend/src/test/java/com/racetracker/security/RequestRateLimiterTest.java`

- [x] **Step 1: Add OAuth and RFID policy methods**

Add `checkOAuth(String ipAddress)` with a 10/900-second IP bucket. Add `checkRfid(String readerKey, String ipAddress)` with a 120/60-second IP bucket and an optional 600/60-second hashed reader-key bucket.

- [x] **Step 2: Extend focused limiter tests**

Capture Redis keys and arguments to prove limits are correct and raw IP/reader-key values are not exposed. Test the optional-key case and the existing blocked/fail-closed behavior.

- [x] **Step 3: Run the focused limiter test**

Run `cd apps/backend && mvn -Dtest=RequestRateLimiterTest test`.

### Task 3: Apply policies at the public controllers

**Files:**
- Modify: `apps/backend/src/main/java/com/racetracker/controller/OAuthController.java`
- Modify: `apps/backend/src/main/java/com/racetracker/controller/RfidController.java`
- Test: `apps/backend/src/test/java/com/racetracker/controller/OAuthControllerWebMvcTest.java`
- Test: `apps/backend/src/test/java/com/racetracker/controller/RfidControllerWebMvcTest.java`

- [x] **Step 1: Inject the limiter and trusted address resolver**

Use constructor injection in both controllers. Replace RFID’s direct forwarded-header parsing with `ClientAddressResolver.resolve`.

- [x] **Step 2: Enforce OAuth throttling before token exchange**

Call `checkOAuth` before `OAuthAccountService.authenticateWithGoogle`.

- [x] **Step 3: Enforce RFID ingest throttling**

Call `checkRfid` before `RfidIngestService.ingest`; leave heartbeat behavior unchanged except for trusted client address resolution.

- [x] **Step 4: Add controller tests**

Verify OAuth passes the resolved client address and RFID passes the supplied reader key plus resolved client address. Verify the service is not called when the limiter throws `TooManyRequestsException`.

- [x] **Step 5: Run focused controller tests**

Run `cd apps/backend && mvn -Dtest=OAuthControllerWebMvcTest,RfidControllerWebMvcTest test`.

### Task 4: Run repository verification and release evidence gates

**Files:**
- No source files expected.

- [x] **Step 1: Run backend verification**

Run `cd apps/backend && mvn -B test` and record whether PostGIS migration smoke is skipped or executed.

- [x] **Step 2: Run JavaScript contract and app checks**

Run `pnpm contracts:validate`, web lint/typecheck/test/build, mobile lint/TypeScript/test, and RFID gateway tests.

- [x] **Step 3: Run reader-adapter checks**

Run Maven tests in `apps/rfid-reader-demo` and `apps/rfid-reader-impinj`.

- [x] **Step 4: Run release evidence checks available locally**

Run `git diff --check`, inspect CI workflow requirements, and run Docker/compose or load/e2e checks only where dependencies and services are available. Report unavailable gates as unverified rather than passing them by inference.

- [x] **Step 5: Review the final diff and prepare a commit message**

Do not commit. Produce one concise Conventional Commit subject derived from the final diff.
