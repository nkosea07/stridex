# Spring Boot 4 migration plan

**Status:** To do. **Deadline:** merged to `main` before **2027-02-17**.
**Owner:** backend maintainer. **Tracking:** `docs/PENDING_WORK.md` §8 and the automated expiry
alert (`.github/workflows/security-exception-expiry.yml`).

## Why

The backend runs Spring Boot 3.5.16 (Spring Framework 6.2.19), the last 3.5 release. Two critical
Spring MVC advisories are fixed only in Spring Framework 7.0.9+:

| Advisory | Issue | Why it is accepted today |
| --- | --- | --- |
| CVE-2026-47884 | Remote code execution through `XsltView` | No XSLT views in the backend |
| CVE-2026-47890 | Server-Sent Events stream corruption with views | No SSE endpoints |

Both are ignored in `.trivyignore` until **2027-02-17**. `UnreachableSpringCveGuardTest` fails the
build if either feature is introduced. Spring Boot 3.5 no longer receives open-source patches, so
further CVEs in Tomcat, Spring Framework, and Spring Security will keep needing manual overrides
until we move. The exception is a bridge, not a fix.

## Target

Use the newest Spring Boot **4.1.x** patch that is at least 14 days old when the work starts
(4.1.2 as of 2026-11-17). Confirm its managed versions, which for 4.1.0 were:

| Component | 3.5.16 today | Boot 4.1 line |
| --- | --- | --- |
| Spring Framework | 6.2.19 | 7.0.x (must be **≥ 7.0.9**) |
| Spring Security | 6.5 | 7.1 |
| Hibernate ORM | 6.6 | 7.4 |
| Jackson | 2.21 (`com.fasterxml.jackson`) | 3.1 (`tools.jackson`) |
| Tomcat | 10.1 (overridden) | 11.0 |
| Flyway | 11.7 | 12.x |
| JUnit | Jupiter 5 | Jupiter 6 |
| springdoc-openapi | 2.8.17 | **3.x** (2.x does not support Boot 4) |

Java 17 remains supported, so the Java baseline stays unchanged and CI keeps running on Java 17.

## Impact inventory (measured 2026-11-17)

| Area | Where | Size |
| --- | --- | --- |
| Jackson 2 API (`com.fasterxml.jackson`, `ObjectMapper`) | services, security, channels, Redis payloads | 19 main files |
| Hibernate-specific annotations | `@JdbcType(PostgreSQLEnumJdbcType.class)` enum columns, `@SQLRestriction`, `@CreationTimestamp` | 40 files, 15 enum mappings |
| `@MockBean` / `@SpyBean` (removed in Boot 4) | controller slice tests | 19 test files |
| `@WebMvcTest` slices (starter/test module split) | controller tests | 17 test files |
| Spring Security config | `SecurityConfig`, `JwtAuthenticationFilter`, `ApiSecurityExceptionHandler` | 3 files |
| STOMP WebSocket | live tracking broker config | 1 file |
| Redis streams | RFID ingest pipeline | 5 files |
| Scheduling / async | workers and notifiers | 11 files |
| Starters renamed or split | `web`, `websocket`, `aop`, `thymeleaf`, `mail`, `data-redis`, `flyway` | `pom.xml` |
| Third-party compatibility | `jjwt-jackson` (Jackson 2), `springdoc`, `firebase-admin`, MapStruct, Lombok | `pom.xml` |
| Config properties | `application.yml` keys renamed in Boot 4 | `application.yml` |

## Steps

Work on one branch, `chore/spring-boot-4`, in this order. Each step should end with
`mvn -B clean test` passing. If a step fails in a way that blocks the next one, fix it before
continuing.

1. **Prepare on 3.5 (separate small PR first).**
   - Replace `@MockBean`/`@SpyBean` with `@MockitoBean`/`@MockitoSpyBean`. These exist since
     Boot 3.4, so the change is safe to merge before the upgrade.
   - Fix all deprecation warnings: `mvn -B -Dmaven.compiler.showDeprecation=true compile`.
   - Optionally run OpenRewrite `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` in dry-run
     mode to preview the automated changes. Review its output; do not apply it wholesale.
2. **Bump the parent and starters.**
   - Parent to the chosen 4.1.x.
   - Rename starters per the Boot 4 migration guide, for example `spring-boot-starter-web` →
     `spring-boot-starter-webmvc`, and add the dedicated Flyway starter or module.
   - Add the matching test starters for the slices in use (WebMvc, Data JPA, Security).
   - Remove the 3.5 property overrides added on 2026-11-16 (`tomcat.version`, `netty.version`,
     `jackson-bom.version`, `postgresql.version`, `httpcore5.version`) and the explicit Guava pin
     if the new BOM resolves patched versions. Keep the `jackson-dataformat-xml` exclusion on
     `firebase-admin`; verify JSON stays the default response type.
3. **Jackson 3.**
   - Migrate imports from `com.fasterxml.jackson` to `tools.jackson`, and the `ObjectMapper`
     construction and configuration to `JsonMapper.builder()`.
   - Check every Redis stream payload and the persisted `notifications.data` JSON for round-trip
     compatibility, using a test that reads JSON written by the 3.5 code.
   - `jjwt-jackson` still targets Jackson 2. Either keep Jackson 2 on the classpath for it only,
     or switch to `jjwt-gson`/a hand-rolled serializer. Decide in this step.
   - `spring.jackson.use-jackson2-defaults=true` may be used temporarily, but remove it before
     merging.
4. **Hibernate 7.**
   - Re-check the PostgreSQL enum mappings (`PostgreSQLEnumJdbcType`), `@SQLRestriction` on
     `CheckpointRecord`, and native queries.
   - Hibernate 7 tightens some HQL/JPQL parsing. Run the full test suite plus the PostGIS Flyway
     smoke test (`FLYWAY_TEST_*`) so `ddl-auto: validate` runs against the real V1–V58 schema.
5. **Spring Security 7.**
   - Update `SecurityConfig` to the 7.x DSL; deprecated `HttpSecurity` methods are removed.
   - Re-run `ControllerAuthorizationMatrixTest`, MFA, JWT, and browser-cookie tests unchanged. They
     define the expected authorization behaviour and must not be weakened to pass.
6. **Web, WebSocket, Redis, mail, Thymeleaf.**
   - Verify STOMP live tracking, Redis stream consumers, and email templates.
   - Upgrade `springdoc-openapi-starter-webmvc-ui` to the newest 3.x that supports the chosen Boot
     version.
7. **Configuration.** Run the Boot 4 properties migrator
   (`spring-boot-properties-migrator`, test scope only) once, apply its renames to
   `application.yml`, then remove the migrator.
8. **Remove the exception.**
   - Delete the two Spring lines from `.trivyignore`.
   - Keep `UnreachableSpringCveGuardTest` or delete it (it is no longer needed once fixed).
   - Update `docs/RELEASE_READINESS.md` and this plan's status.

## Verification (all must pass before merge)

- `apps/backend`: `mvn -B clean test` on Java 17, with the PostGIS Flyway smoke test enabled.
- CI on the PR: `backend`, `load-smoke` (boots against PostGIS), `containers` (Trivy image scan
  with the Spring CVEs no longer ignored), and `dependency-security`.
- Manual smoke on a staging deployment:
  - Admin web sign-in with MFA, then an athlete sign-in on mobile.
  - Registration and payment checkout start.
  - RFID simulated ingest.
  - The live tracking map updating.
  - Results publish and certify.
  - Email delivery.
  - Push delivery.
- Contract unchanged: `pnpm contracts:validate`, plus web and mobile tests against the staging
  API.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| JSON shape changes under Jackson 3 (dates, nulls, enums) break web or mobile clients | Snapshot tests of key DTO responses taken on 3.5 before the upgrade; compare after |
| Hibernate 7 query or mapping differences surface only on PostGIS | Flyway smoke test and `load-smoke` in CI; staging run before production |
| Third-party libraries lag Boot 4 (springdoc, jjwt, firebase-admin) | Check each in step 2; pin known-compatible versions at least 14 days old |
| The work slips past 2027-02-17 | The expiry alert opens an issue from 2027-01-03. If not done, re-review both CVEs and renew the `.trivyignore` lines for a short, documented period; do not delete them silently |

## Rollback

The migration is a single PR. If production shows problems, revert the merge commit and redeploy
the previous image. No database migration is part of this work. If one becomes necessary, it must
be backward compatible with the 3.5 build.
