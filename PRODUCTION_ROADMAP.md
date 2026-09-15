# CMS2 Production Roadmap

**Prepared by:** Principal Software Architect review, 2026-09-14
**Scope:** Full repository — `backend/` (Java 21 / Spring Boot 3.3.5 / Gradle / Flyway / Postgres) and `frontend/` (React 18 / TypeScript / Vite / Tailwind CSS 4).
**Method:** Static inspection of module layout, build config, test structure, security config, and prior audit history (`HANDOFF.md` Parts 1–3b, `_diagnostics/`). No code was changed to produce this document.

> Context this roadmap builds on: two full audit-and-fix passes have already landed (see `HANDOFF.md`) — a cross-tenant authorization hole, JWT hardcoded-secret fallbacks, a schedule-overlap race condition, and a batch of DTO validation gaps are already fixed and verified. This roadmap does **not** re-litigate those. It looks one level up, at whether the *repository itself* — its process, tooling, and structural conventions — is production-grade, independent of any single feature's correctness.

---

## 1. Current State Assessment

### 1.1 The most fundamental gap: there is no version control

`git status` at the repo root returns "not a git repository." Every fix from the last two audit sessions exists only as working-tree edits with no commit history, no diff-based review trail, no rollback path, and no branch protection. **This blocks everything else in this roadmap** — CI, code review, Dependabot/Renovate, and blame-based debugging all require git. This is Phase 1, item zero.

### 1.2 Architecture: better than average, but inconsistently layered

The backend already follows **package-by-feature** (`com.cms.booking`, `com.cms.scheduling`, `com.cms.identity`, `com.cms.waitlist`, `com.cms.clinical`, `com.cms.discovery`, `com.cms.inbox`, `com.cms.notification`, `com.cms.patient`), which is the right call for a multi-tenant domain like this and matches the project's own [constitution](.specify/memory/constitution.md) (Principle III: "modular... rather than a single undifferentiated service layer"). This should **not** be flattened into a top-level `controllers/`, `services/`, `repositories/` split — that would break the module boundaries the constitution explicitly mandates and make cross-cutting changes (e.g. "how does booking cancellation work end-to-end") harder to trace, not easier.

The real problem is **inconsistent internal layering within each module**:
- `com.cms.booking` alone has **62 files in one flat package** (controllers, services, entities, and ~18 exception classes all as siblings), with only `dto/` broken out as a subpackage. Finding "the service that owns X" means scanning filenames by suffix, not browsing a directory.
- Some modules (`scheduling`, `clinical`) follow the same flat pattern; none consistently separate `api/` (controllers + request/response shapes), `service/`, `repository/`, `domain/` (entities), and `exception/` as subpackages.
- **Security configuration is scattered across 5 independent classes** with no central index: `patient/account/SecurityConfig.java`, `identity/account/SecurityConfig.java`, `identity/admin/SuperAdminSecurityConfig.java`, `booking/BookingSecurityConfig.java`, `discovery/DiscoverySecurityConfig.java`. Each is presumably correct in isolation (the Phase-1 audit this session found every clinic-scoped controller correctly enforces membership checks), but there is no single file or doc that lets a reviewer see "here is the entire authentication/authorization posture of this API" at a glance.

### 1.3 Tight coupling on the frontend: no shared API client

Every one of the ~28 `features/*/api.ts` files independently:
- Re-declares `const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'`
- Re-implements `fetch(...)` with a manually attached `Authorization: Bearer ${token}` header
- Re-implements its own `*ApiError` class to parse a non-2xx response body

This isn't just duplication — it already caused a real, currently-shipped bug (found in this session's audit): every `*ApiError` class's fallback logic makes the backend's specific validation message **unreachable dead code** in all 18 files that follow the pattern, because the shared helper they each copy-pasted (`defaultMessageFor`) always has a `default:` branch. One shared client would have made this a one-file fix instead of an 18-file bug.

### 1.4 No CI, no containerization, no observability

- **No `.github/workflows/`** (or any CI config) exists. Nothing enforces that `gradle test`, `npx tsc -b`, `npx vitest run`, or `npx oxlint` actually pass before code is considered done — today that's entirely manual discipline.
- **No `Dockerfile` or `docker-compose.yml`** anywhere in the repo, despite the backend depending on Postgres and on Testcontainers for its integration tests. A new contributor has no one-command way to stand up a local database.
- **No `spring-boot-starter-actuator`** dependency — there is no `/health` or `/metrics` endpoint. In production this means no load-balancer health check, no basic uptime signal, nothing for an APM/monitoring agent to scrape.
- **No rate limiting or account lockout** on `StaffAuthController` / `PatientAccountController` login endpoints — unbounded brute-force attempts against bcrypt hashes (flagged this session, not yet fixed).

### 1.5 Test pyramid is inverted, and the sandbox can't run most of it anyway

Backend test folders:

| Module | unit/ | integration/ | contract/ |
|---|---|---|---|
| `identity` | ✅ | ✅ | ✅ |
| `patient` | ✅ | ✅ | ✅ |
| `booking` | ❌ | ✅ | ❌ |
| `scheduling` | ❌ | ✅ | ❌ |
| `waitlist` | ❌ | ✅ | ❌ |
| `clinical` | ❌ | ✅ | ❌ |
| `discovery` | ❌ | ✅ | ❌ |
| `notification` | ❌ | ✅ | ❌ |
| `inbox` | ❌ | ✅ | ❌ |

7 of 9 modules — including the business-critical `booking` and `scheduling` modules — have **only** Testcontainers-backed integration tests and no isolated unit-test layer. Combined with the confirmed sandbox limitation that Docker/Testcontainers cannot run in this environment (see project memory `docker_testcontainers_sandbox_limitation`), this means most of the backend's own test suite is **unverifiable outside a real CI/dev machine with Docker** — there's no fast, dependency-free feedback loop for the majority of business logic.

### 1.6 Source-tree hygiene

- **48 `.impeccable/hook.cache.json` files** are physically present inside `backend/src/main/java/**` and `backend/src/test/java/**` package directories — tool-cache artifacts sitting alongside real source files. There is also no root-level `.gitignore` (only `frontend/.gitignore` exists) to keep these, `backend/build/`, `.idea/`, or IDE files out once git is initialized.
- `frontend/frontend-run.log` — a runtime log file — sits committed-adjacent in the frontend root rather than in a gitignored location.
- No `.env.example` anywhere documenting the full set of environment variables a deployment needs (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_CORS_ALLOWED_ORIGINS`, `PATIENT_JWT_SECRET`, `STAFF_JWT_SECRET`, `SUPER_ADMIN_JWT_SECRET`, `SUPER_ADMIN_USERNAME`, `SUPER_ADMIN_PASSWORD`, `ADMIN_REJECTION_RETENTION_DAYS`, `PORT`, `VITE_API_BASE_URL`) — a new deployer has to grep `application.yml` and `vite.config.ts` by hand to discover them.

### 1.7 What's already good (don't regress these)

- `application.yml`'s secret handling is genuinely well done: every sensitive value (`PATIENT_JWT_SECRET`, `STAFF_JWT_SECRET`, `SUPER_ADMIN_JWT_SECRET`, `SUPER_ADMIN_USERNAME/PASSWORD`) has **no committed default** and fails safe to a randomly generated value at startup rather than a guessable one — and each is documented in-line with the rationale and the audit finding that produced it.
- Flyway migrations are strictly forward-only, one file per change, cleanly numbered (`V1`–`V31`), with no edits to shipped migrations.
- Spotless is already wired to run on every `compileJava` — auto-formatting is enforced, not just suggested.
- `oxlint` is configured with the a11y/react/react-perf/import plugin set — better default coverage than a bare ESLint setup, and it's fast enough to run locally on every save.
- Every clinic-scoped controller checked in this session's audit correctly enforces clinic-membership authorization; JPQL/`@Query` usage is fully parameterized (no injection surface found).

---

## 2. Target Architecture

Keep the existing **domain-module boundary** (Principle III of the constitution) as the top-level structure. Fix the *internal* layering within each module, and add the one cross-cutting piece the frontend is missing (a shared HTTP client). Do not introduce a horizontal `controllers/`, `services/`, `repositories/` split at the top of `com.cms` — that would fight the constitution, not fulfill it.

### Backend — per-module internal structure

```
backend/src/main/java/com/cms/booking/
├── api/                          # Controller layer — HTTP concerns only
│   ├── BookingController.java
│   ├── PatientBookingController.java
│   └── ...
├── dto/                          # Request/response schemas (already exists — keep)
│   ├── BookSlotRequest.java
│   └── BookingResponse.java
├── service/                      # Business logic — no HTTP/JPA leakage into callers
│   ├── PatientBookingService.java
│   ├── StaffBookingService.java
│   └── FeeResolutionService.java
├── repository/                   # Spring Data interfaces only
│   ├── BookingRepository.java
│   └── DoctorDefaultFeeRepository.java
├── domain/                       # JPA entities + enums
│   ├── Booking.java
│   ├── BookingStatus.java
│   └── PaymentStatus.java
├── exception/                    # Module-specific exceptions + the module's @ExceptionHandler
│   ├── BookingExceptionHandler.java
│   ├── SlotAlreadyBookedException.java
│   └── ...
└── config/                       # BookingSecurityConfig, etc.
```

Apply this same shape to every module. This is a mechanical, IDE-assisted move (package rename), not a rewrite — behavior is unchanged, only file location and package declarations move, so the existing test suite is the correctness proof at each step.

### Frontend — introduce one shared HTTP layer, keep the rest

```
frontend/src/
├── lib/
│   └── apiClient.ts       # NEW — one authenticated-fetch helper + one error-parsing
│                          #       function every feature/*/api.ts calls instead of
│                          #       hand-rolling fetch + headers + *ApiError per file
├── features/
│   └── <domain>/
│       ├── api.ts         # now: thin wrapper calling lib/apiClient — no fetch() calls
│       ├── <Component>.tsx
│       └── ...
```

The `features/<domain>/` folder-per-domain structure already mirrors the backend's module boundaries well (e.g. `features/waitlist` ↔ `com.cms.waitlist`) — keep it. The only structural addition is `lib/apiClient.ts` as the single seam for base URL, auth-header injection, and error-body parsing.

### Cross-cutting additions (net-new, not restructuring)

- `backend/src/main/java/com/cms/common/observability/` — houses actuator configuration once added (Phase 3).
- Root `.gitignore`, root `.env.example`, `docker-compose.yml`, `.github/workflows/ci.yml` — all net-new files, zero risk to existing code.

---

## 3. Five-Phase Upgrade Plan

Each phase is independently shippable and testable — the app must build and all existing tests must still pass at the end of every phase before starting the next.

### Phase 1 — Foundation & Safety Nets (no application code changes)
**Goal:** make every subsequent phase reviewable, reversible, and reproducible.
1. `git init`; commit current state as-is (this is the baseline, warts included).
2. Add a root `.gitignore` (backend `build/`, `.gradle/`, `.impeccable/`, IDE folders; frontend already has its own).
3. Delete or gitignore-and-remove the 48 `.impeccable/hook.cache.json` files from `src/main` and `src/test` — they're tool cache, not source.
4. Add `docker-compose.yml` with a single Postgres service matching `application.yml`'s defaults (`cms`/`cms`/`cms`), so `docker compose up -d db` is the entire local setup step.
5. Add a root `.env.example` documenting every environment variable listed in §1.6.
6. **Verify:** `gradle compileJava compileTestJava` and `npm run build` still succeed — nothing above touches application code, so this should be a no-op on behavior.

### Phase 2 — Code Hygiene & Quality Gates (zero-budget tooling, still no behavior changes)
**Goal:** stop relying on manual discipline to catch regressions.
1. Add a GitHub Actions workflow (`.github/workflows/ci.yml`) with two jobs: `backend` (`gradle build`, which already runs Spotless + tests) and `frontend` (`npm ci && npm run lint && npx tsc -b && npm run test`). Runs on every push/PR — free on GitHub for this repo size.
2. Add the OWASP Dependency-Check Gradle plugin (free/OSS) to `backend/build.gradle`, failing the build on known-CVE dependencies above a severity threshold.
3. Enable **Dependabot** (free, built into GitHub) for both `backend` (Gradle) and `frontend` (npm) — automatic PRs for vulnerable/outdated dependencies.
4. Add Jacoco to `backend/build.gradle` for coverage *reporting* (not gating yet — the module test-pyramid gap in §1.5 means gating on coverage now would just block on the Testcontainers/Docker limitation; report first, gate later once Phase 5's unit-test backfill lands).
5. **Verify:** CI is green on the current codebase before any further phase begins — this phase's entire purpose is the safety net for Phases 3–5.

### Phase 3 — Backend Layering & Security Consolidation
**Goal:** make each module's internal structure match §2, and make the security posture auditable in one place.
1. One module at a time (smallest first — e.g. `inbox`, then `notification`, working up to `booking` last), move files into `api/`, `service/`, `repository/`, `domain/`, `exception/` subpackages per §2. Run the full test suite after each module — a passing suite is the proof the move was mechanical.
2. Add `spring-boot-starter-actuator`, expose only `/actuator/health` publicly (everything else `never` per Spring Security config), so there's a load-balancer-checkable liveness endpoint.
3. Write one `SECURITY.md` (or a `docs/security-posture.md`) that indexes all 5 existing `SecurityConfig` classes with a one-line description of what each filter chain covers — not a code change, a map of what already exists, so a reviewer doesn't have to grep for it.
4. **Verify:** `gradle test` green after every module move; manually hit `/actuator/health` locally to confirm it's live and that no other actuator endpoint is exposed.

### Phase 4 — Frontend API Layer Consolidation
**Goal:** eliminate the 28-file fetch/error-handling duplication from §1.3, fixing the unreachable-error-message bug as a byproduct.
1. Build `frontend/src/lib/apiClient.ts`: one function that takes a path + options, injects the base URL and `Authorization` header, and on a non-2xx response parses the body once and actually surfaces the backend's specific message (fixing the dead-code bug found this session).
2. Migrate `features/*/api.ts` files to call it one feature at a time (start with a low-traffic feature like `partial-session-cancellation` to prove the pattern, finish with high-traffic ones like `patient-booking`). After each migration, run `npx vitest run` for that feature's tests plus the full suite.
3. Delete the now-redundant per-file `*ApiError` class bodies once each feature is migrated — don't leave both paths alive.
4. **Verify:** 236/236 (or current count) frontend tests still passing after the full migration; manually spot-check one error case per migrated feature in-browser to confirm the specific backend message now actually surfaces (this is a user-visible behavior change, worth eyeballing, not just trusting the test suite).

### Phase 5 — Hardening & Scalability
**Goal:** close the remaining security/scale gaps identified in this session's Phase-1 audit, now that Phases 1–4 give a safety net to do it against.
1. Add rate limiting/lockout to `StaffAuthController` and `PatientAccountController` login endpoints (Bucket4j is free/OSS and needs no external service — in-memory token bucket per IP+account is sufficient at this scale).
2. Add `Pageable`/a hard page-size cap to the public `/api/v1/discovery/search` endpoint.
3. Redact or gate `LoggingNotificationSender`'s unconditional INFO-level logging of recipient contact info + message body behind a debug-only flag, off by default.
4. Consolidate the duplicated `30 * 60` waitlist-offer-expiry literal (`WaitlistEntry.java`, `WaitlistMatchingService.java`) into one named constant.
5. Backfill true unit tests (no Testcontainers, mocked repositories) for the 7 modules currently integration-only (§1.5), prioritizing `booking` and `scheduling` — this is what finally makes Jacoco coverage gating (deferred from Phase 2) meaningful, and gives this sandbox environment a test layer it can actually run.
6. **Verify:** each hardening item ships with its own test proving the vulnerability is closed (e.g. a test asserting the 6th rapid login attempt within a window is rejected) — this is also where Test-First (constitution Principle I) applies most literally: write the failing "6th attempt is rejected" test before implementing the limiter.

---

## 4. Security & Zero-Budget Checklist

### Environment & config
- [ ] Root `.env.example` covering every variable in §1.6 (Phase 1)
- [ ] Root `.gitignore` covering `build/`, `.gradle/`, `.impeccable/`, `*.log` (Phase 1)
- [ ] `docker-compose.yml` for local Postgres, matching `application.yml` defaults exactly (Phase 1)
- [ ] No secret ever has a committed non-empty default — already true for JWT secrets/admin creds; keep this invariant for any new secret

### OWASP Top 10 — current standing
| Risk | Status | Action |
|---|---|---|
| A01 Broken Access Control | ✅ Verified clean this session — every clinic-scoped controller checks membership | Keep enforcing on every new endpoint |
| A02 Cryptographic Failures | ✅ JWT secrets externalized, fail-safe random default | Terminate TLS in front of Spring in any real deployment (not app-level) |
| A03 Injection | ✅ All `@Query` usage parameterized | Keep enforcing in code review |
| A04 Insecure Design | ⚠️ No rate limiting on login | Phase 5 item 1 |
| A05 Security Misconfiguration | ⚠️ No actuator, no documented security-posture map | Phase 3 items 2–3 |
| A06 Vulnerable Components | ❌ No dependency scanning at all | Phase 2 items 2–3 |
| A07 Auth Failures | ⚠️ No lockout/brute-force protection | Phase 5 item 1 |
| A08 Data Integrity Failures | ✅ Flyway forward-only, no edited migrations | Maintain the convention |
| A09 Logging/Monitoring Failures | ⚠️ PII logged unconditionally at INFO; no actuator/metrics | Phase 3 item 2, Phase 5 item 3 |
| A10 SSRF | N/A — no outbound user-controlled URLs in current feature set | Re-check if a future feature adds one |

### Free tooling to adopt (all zero-budget)
- **GitHub Actions** — CI (Phase 2)
- **Dependabot** — dependency + vulnerability PRs, built into GitHub, free (Phase 2)
- **OWASP Dependency-Check** (Gradle plugin) — free, CVE scanning for JVM deps (Phase 2)
- **npm audit** — free, already available, just needs a CI step (Phase 2)
- **Jacoco** — free coverage reporting (Phase 2, gating in Phase 5)
- **Spotless** — already adopted, keep it
- **oxlint** — already adopted (with a11y/react/perf/import plugins), keep it, add to CI (Phase 2)
- **Bucket4j** — free, in-memory rate limiting, no external Redis/service required at this scale (Phase 5)
- **Trivy** — free container image scanner, worth adding once Phase 1's `docker-compose.yml`/any future Dockerfile exists

---

## Summary

Three things dominate every other item on this list:
1. **No git repository exists.** This is not a style preference — it blocks CI, Dependabot, meaningful code review, and rollback. Everything else in this roadmap assumes it's fixed first.
2. **The test suite that would catch regressions in the riskiest modules (booking, scheduling) can't run in this sandbox and doesn't fully exist as unit tests anywhere** — 7 of 9 backend modules are integration-test-only, and Testcontainers/Docker is confirmed unavailable here. Structural fixes in this roadmap need a real CI machine to be verified end-to-end, not just this environment.
3. **The frontend's 28 duplicated `fetch`+error-handling implementations already produced one shipped bug** (unreachable backend error messages, 18 files) — consolidating to one `apiClient.ts` is the single highest-leverage frontend change available, both for this bug and for every future one like it.
