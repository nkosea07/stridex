# Review Risk Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the pushed frontend build failures and close the authentication/session-management gaps identified in code review without expanding scope beyond the reviewed risks.

**Architecture:** Resolve the web issues first so the app can compile again, then harden backend authentication by making refresh-token usage revocable and by enforcing email-verification before protected access. Keep changes localized to the existing web API layer, dashboard/live-tracking UI, and backend auth services rather than restructuring unrelated modules.

**Tech Stack:** React 19, TypeScript, Vite, Spring Boot, Spring Security, JPA, PostgreSQL, pnpm, Maven/JUnit

---

## Target Files

- Modify: `apps/web/src/pages/LiveTrackingPage.tsx`
- Modify: `apps/web/src/components/dashboard/SecurityAuditor.tsx`
- Modify: `apps/web/src/components/dashboard/AnalyticsTray.tsx`
- Modify: `apps/web/src/types/index.ts`
- Modify: `apps/web/src/lib/api.ts`
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/AuthServiceImpl.java`
- Modify: `apps/backend/src/main/java/com/racetracker/security/JwtService.java`
- Modify: `apps/backend/src/main/java/com/racetracker/security/CustomUserDetailsService.java`
- Modify: `apps/backend/src/main/java/com/racetracker/controller/AuthController.java`
- Modify: `apps/backend/src/main/java/com/racetracker/dto/auth/AuthResponse.java`
- Modify: `apps/backend/src/main/java/com/racetracker/repository/AuthTokenRepository.java`
- Modify: `apps/backend/src/main/java/com/racetracker/model/AuthToken.java`
- Modify: `apps/backend/src/main/resources/application.yml`
- Modify: `apps/backend/database/schema.sql`
- Modify: `apps/backend/src/test/java/com/racetracker/service/AuthServiceImplSecurityFlowTest.java`
- Modify: `apps/backend/src/test/java/com/racetracker/service/AuthServiceImplRefreshTokenTest.java`
- Create or modify: `apps/web/src/components/ui/alert.tsx`
- Create or modify: `apps/web/src/pages/LiveTrackingPage.test.tsx` or nearest existing web test file if the repo already has a preferred pattern
- Create or modify: `apps/web/src/components/dashboard/AnalyticsTray.test.tsx` or nearest existing web test file if the repo already has a preferred pattern

### Task 1: Restore the web build contract

**Files:**
- Modify: `apps/web/src/pages/LiveTrackingPage.tsx`
- Modify: `apps/web/src/components/dashboard/SecurityAuditor.tsx`
- Modify: `apps/web/src/components/dashboard/AnalyticsTray.tsx`
- Modify: `apps/web/src/types/index.ts`
- Modify: `apps/web/src/lib/api.ts`
- Create or modify: `apps/web/src/components/ui/alert.tsx`

- [ ] **Step 1: Write down the intended type/API contract before editing**

Document in the working notes:
- `LiveTrackingPage` needs `Race` and `racesApi`
- Live tracking rows need either an explicit `ExtendedLiveTrackingData` type or the UI must stop reading undeclared fields
- `SecurityAuditor` must import a real UI primitive from the web app source tree

- [ ] **Step 2: Re-run the failing web build and capture the current baseline**

Run: `pnpm --filter ./apps/web build`
Expected: FAIL with the current TypeScript errors around `LiveTrackingPage`, `SecurityAuditor`, and `AnalyticsTray`

- [ ] **Step 3: Fix `LiveTrackingPage` imports and local types minimally**

Implementation notes:
- Import `Race` from `@/types`
- Import `racesApi` from `@/lib/api`
- Either define `type ExtendedLiveTrackingData = LiveTrackingData & { lastSeen?: number; splits?: Array<{ name: string; time: string }> }` locally, or remove all reads of fields that are not part of the actual API payload
- Keep scope narrow: do not redesign the page

- [ ] **Step 4: Fix `SecurityAuditor` to use an existing alert component**

Implementation notes:
- If the repo already has a compatible alert implementation outside `apps/web/src`, copy or port the minimal alert component into `apps/web/src/components/ui/alert.tsx`
- Otherwise replace the alert usage with existing local UI primitives that already build cleanly

- [ ] **Step 5: Align `AnalyticsTray` with the real live-tracking data shape**

Implementation notes:
- If `splits` are not available from the backend, remove the `splits` UI branch or guard it behind an explicit optional extension type
- If `splits` are meant to exist, add them to the frontend type only after confirming the backend or websocket payload actually includes them

- [ ] **Step 6: Run the web build again**

Run: `pnpm --filter ./apps/web build`
Expected: PASS

- [ ] **Step 7: Commit the frontend build fix**

```bash
git add apps/web/src/pages/LiveTrackingPage.tsx apps/web/src/components/dashboard/SecurityAuditor.tsx apps/web/src/components/dashboard/AnalyticsTray.tsx apps/web/src/types/index.ts apps/web/src/lib/api.ts apps/web/src/components/ui/alert.tsx
git commit -m "fix(web): restore live tracking build"
```

### Task 2: Make refresh-token usage revocable

**Files:**
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/AuthServiceImpl.java`
- Modify: `apps/backend/src/main/java/com/racetracker/security/JwtService.java`
- Modify: `apps/backend/src/main/java/com/racetracker/repository/AuthTokenRepository.java`
- Modify: `apps/backend/src/main/java/com/racetracker/model/AuthToken.java`
- Modify: `apps/backend/database/schema.sql`
- Modify: `apps/backend/src/test/java/com/racetracker/service/AuthServiceImplRefreshTokenTest.java`

- [ ] **Step 1: Write the failing refresh-token tests**

Add tests for:
- refresh succeeds only for a stored, unconsumed, unexpired refresh token
- refresh fails after token revocation/consumption
- refresh fails after password reset if old refresh tokens are invalidated

- [ ] **Step 2: Run the focused refresh-token test class**

Run: `mvn -Dtest=AuthServiceImplRefreshTokenTest test`
Expected: FAIL because the current implementation accepts any signed refresh JWT

- [ ] **Step 3: Pick one token source of truth and apply it consistently**

Preferred approach:
- Reuse `auth_tokens` by adding a `REFRESH` token type
- Persist refresh tokens when issuing them
- On refresh, require a matching stored token record that is unconsumed and unexpired
- Mark used refresh tokens consumed and rotate to a newly stored refresh token

Avoid:
- Keeping the unused `refresh_tokens` table in schema while validating against something else

- [ ] **Step 4: Implement revocation hooks**

Implementation notes:
- Invalidate outstanding refresh tokens on password reset
- Invalidate outstanding refresh tokens for the authenticated user on logout if a logout endpoint already exists or is added as part of the current auth surface
- If logout endpoint addition is out of scope, still ensure password reset and future refresh rotation revoke prior tokens

- [ ] **Step 5: Re-run the focused refresh-token tests**

Run: `mvn -Dtest=AuthServiceImplRefreshTokenTest test`
Expected: PASS

- [ ] **Step 6: Commit the refresh-token hardening**

```bash
git add apps/backend/src/main/java/com/racetracker/service/impl/AuthServiceImpl.java apps/backend/src/main/java/com/racetracker/security/JwtService.java apps/backend/src/main/java/com/racetracker/repository/AuthTokenRepository.java apps/backend/src/main/java/com/racetracker/model/AuthToken.java apps/backend/database/schema.sql apps/backend/src/test/java/com/racetracker/service/AuthServiceImplRefreshTokenTest.java
git commit -m "fix(auth): make refresh tokens revocable"
```

### Task 3: Enforce email verification before protected access

**Files:**
- Modify: `apps/backend/src/main/java/com/racetracker/service/impl/AuthServiceImpl.java`
- Modify: `apps/backend/src/main/java/com/racetracker/security/CustomUserDetailsService.java`
- Modify: `apps/backend/src/main/java/com/racetracker/dto/auth/AuthResponse.java`
- Modify: `apps/backend/src/test/java/com/racetracker/service/AuthServiceImplSecurityFlowTest.java`

- [ ] **Step 1: Write the failing verification tests**

Add tests for:
- register does not grant full authenticated access before verification, or explicitly returns a response that the frontend can use without treating the user as logged in
- login rejects unverified users with a specific, non-ambiguous error

- [ ] **Step 2: Run the focused auth security test class**

Run: `mvn -Dtest=AuthServiceImplSecurityFlowTest test`
Expected: FAIL once the new verification assertions are added

- [ ] **Step 3: Decide and encode the product behavior**

Preferred behavior:
- Registration creates the user and sends verification email
- Registration response does not issue usable access/refresh tokens for unverified users
- Login rejects users whose `emailVerified` flag is false

Alternative only if already required by product:
- Return a limited response with `requiresEmailVerification=true` and no session tokens

- [ ] **Step 4: Implement verification enforcement in the auth path**

Implementation notes:
- Add the `emailVerified` gate after authentication and before `buildAuthResponse`
- Do not rely only on frontend behavior
- Consider whether `CustomUserDetailsService` should also reflect verification status using the `enabled` flag if that matches the desired security model

- [ ] **Step 5: Re-run the focused auth security tests**

Run: `mvn -Dtest=AuthServiceImplSecurityFlowTest test`
Expected: PASS

- [ ] **Step 6: Commit the verification enforcement**

```bash
git add apps/backend/src/main/java/com/racetracker/service/impl/AuthServiceImpl.java apps/backend/src/main/java/com/racetracker/security/CustomUserDetailsService.java apps/backend/src/main/java/com/racetracker/dto/auth/AuthResponse.java apps/backend/src/test/java/com/racetracker/service/AuthServiceImplSecurityFlowTest.java
git commit -m "fix(auth): enforce email verification before login"
```

### Task 4: Run regression checks across the touched surfaces

**Files:**
- Modify only if needed based on failing tests/builds from prior tasks

- [ ] **Step 1: Run the backend auth-focused tests together**

Run: `mvn -Dtest=AuthServiceImplRefreshTokenTest,AuthServiceImplSecurityFlowTest test`
Expected: PASS

- [ ] **Step 2: Run the web build again after all backend/frontend changes are merged**

Run: `pnpm --filter ./apps/web build`
Expected: PASS

- [ ] **Step 3: Run the contract validation script**

Run: `pnpm contracts:validate`
Expected: PASS

- [ ] **Step 4: Smoke-check for stale API assumptions**

Manual checklist:
- Web login flow handles “verification required” response cleanly
- Live tracking page renders without reading undeclared properties
- Security auditor renders with local UI primitives only

- [ ] **Step 5: Commit the final cleanup if any regression fix was required**

```bash
git add .
git commit -m "test: cover auth and web regression fixes"
```

### Task 5: Document the security behavior change

**Files:**
- Modify: `README.md`
- Modify: `apps/backend/README.md`

- [ ] **Step 1: Update auth behavior docs**

Document:
- registration now requires email verification before login
- refresh tokens are rotated/revocable
- any new env var or schema expectation introduced by the auth changes

- [ ] **Step 2: Verify docs match the implementation**

Manual check:
- README instructions do not claim immediate login after registration if that is no longer true
- backend auth docs explain refresh-token invalidation behavior accurately

- [ ] **Step 3: Commit the doc update**

```bash
git add README.md apps/backend/README.md
git commit -m "docs: describe auth hardening behavior"
```

## Exit Criteria

- `pnpm --filter ./apps/web build` passes
- `mvn -Dtest=AuthServiceImplRefreshTokenTest,AuthServiceImplSecurityFlowTest test` passes
- Unverified users cannot obtain or continue authenticated sessions
- Refresh tokens are persisted, rotated, and revocable
- No frontend file references missing modules or undeclared live-tracking fields
