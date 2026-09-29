# Security Posture

Index of every authentication/authorization surface in the CMS2 backend, so the entire security posture is readable in one file instead of cross-referencing 7 separate `SecurityConfig` classes. Written from a direct read of the live code (2026-09-15) — if this drifts from the code, the code is authoritative; update this file, don't trust it blindly.

The backend has **7 independent `SecurityFilterChain` beans**, each scoped to its own `securityMatcher(...)` path prefix so exactly one governs any given request (Spring Security requires a request to match at most one chain's matcher; a request matching none falls through completely ungoverned — see the "Unmatched paths" note at the bottom). `@Order` determines evaluation priority, lowest first.

| # | Order | Class | Path prefix | Realm / audience | Default posture |
|---|---|---|---|---|---|
| 1 | 1 | `com.cms.identity.account.config.SecurityConfig` (`filterChain`) | `/api/v1/clinics/**` | Staff (bearer JWT, no role claim — role checked per-request via `RoleAssignmentRepository`) | `POST /api/v1/clinics/register` explicitly `permitAll()` (clinic self-registration); **every other path `authenticated()`** (fail-closed since 065-phase1-stabilization) |
| 2 | 2 | `com.cms.patient.account.config.SecurityConfig` (`patientFilterChain`) | `/api/v1/patients/**` | Patient (bearer JWT, audience `PATIENT`) | `POST /api/v1/patients/signup` and `/login` explicitly `permitAll()`; **every other path `authenticated()`** (fail-closed since 065-phase1-stabilization) |
| 3 | 3 | `com.cms.identity.admin.config.SuperAdminSecurityConfig` (`adminFilterChain`) | `/api/v1/admin/**` | Super Admin (bearer JWT, audience `SUPER_ADMIN`, issued at the same `/api/v1/staff/login` endpoint as staff — see 040-super-admin-rbac-login) | `authenticated()` |
| 4 | 4 | `com.cms.identity.account.config.SecurityConfig` (`staffAuthFilterChain`) | `/api/v1/staff/**` | Public (unauthenticated) | `permitAll()` — this is the login/signup surface itself, necessarily reachable with no prior token |
| 5 | 5 | `com.cms.discovery.DiscoverySecurityConfig` (`discoveryFilterChain`) | `/api/v1/discovery/**` | Public (unauthenticated) | `permitAll()` — public clinic/doctor search, no credential of any kind ever read |
| 6 | 6 | `com.cms.booking.config.BookingSecurityConfig` (`bookingFilterChain`) | `/api/v1/doctors/**` | Staff (same bearer-JWT machinery as #1, doctor-scoped rather than clinic-scoped — fee/appointment-type configuration) | `authenticated()` |
| 7 | 7 | `com.cms.common.ActuatorSecurityConfig` (`actuatorFilterChain`) | `/actuator/**` | N/A (operational, not a JWT realm) | `permitAll()` for `/actuator/health` only, `denyAll()` for everything else under this prefix — added by 045-backend-module-layering; only `health` is even registered as a web endpoint (`management.endpoints.web.exposure.include: health` in `application.yml`), so no other actuator endpoint exists to be reached regardless |

## Three independent JWT realms, no shared session state

Per `CLAUDE.md`: staff, patient, and Super Admin each have their own signing secret (`STAFF_JWT_SECRET`/`PATIENT_JWT_SECRET`/`SUPER_ADMIN_JWT_SECRET`), their own `JwtService`, and their own `JwtAuthenticationFilter`/`AuthenticationEntryPoint` pair (all now grouped under each module's `config/` subpackage per 045-backend-module-layering). A valid token in one realm does not satisfy another chain's guard, even though staff and Super Admin tokens are both issued from the same `/api/v1/staff/login` endpoint (040-super-admin-rbac-login unified the login screen, not the token realms). None of the three JWT secrets have a committed default — an unset one generates a random signing key at startup (fail-safe, not fail-open), logged as a `WARN`.

## Unmatched paths fall through ungoverned, not denied

A request matching none of the 7 `securityMatcher` prefixes above (e.g. `/docs`, `/api-docs` — the Swagger UI and raw OpenAPI spec) is **not** protected by Spring Security at all; it's served exactly as if `spring-boot-starter-security` weren't on the classpath. This is a deliberate, existing pattern for genuinely public framework/static surfaces — but it means introducing a new endpoint outside all 7 prefixes silently makes it public, a bug class this project has already found and fixed twice before (014, 016) for ordinary business endpoints. **Any new path outside these 7 prefixes needs its own explicit chain or an explicit matcher added to an existing one — never assume a default-deny posture exists.** *Inside* the `/api/v1/clinics/**` and `/api/v1/patients/**` prefixes, a default-deny posture does exist since 065-phase1-stabilization: new endpoints there are authenticated automatically, and only a new *public* endpoint needs an explicit `permitAll()` matcher.

## Public (unauthenticated) surfaces, summarized

- `POST /api/v1/clinics/register` — clinic + first ClinicAdmin registration
- `POST /api/v1/staff/login` — the only endpoint under chain #4's prefix; staff are onboarded by an already-authenticated ClinicAdmin via `POST /api/v1/clinics/{clinicId}/staff` (chain #1, `authenticated()`), not via public self-signup
- `POST /api/v1/patients/login`, `/api/v1/patients/signup` — explicit `permitAll()` matchers in chain #2 (065-phase1-stabilization; previously reached via the trailing `anyRequest().permitAll()`, now removed)
- `GET /api/v1/discovery/**` — chain #5, entirely public
- `GET /actuator/health` — chain #7

Everything else requires a valid bearer JWT for the matching realm, checked per-request (role/clinic-membership authorization happens inside each controller/service via `RoleAssignmentRepository` lookups, not by reading a role claim out of the token — no realm's JWT carries a role claim).

## Rate limiting

`com.cms.common.RateLimitingFilter`/`RateLimitingConfig` apply a hand-rolled fixed-window limiter (not tied to any of the 7 chains above — registered independently via `FilterRegistrationBean`, so it runs regardless of which chain later processes the request) to exactly 4 paths: `/api/v1/staff/login`, `/api/v1/patients/login`, `/api/v1/patients/signup`, `/api/v1/clinics/register`. Configurable via `RATE_LIMIT_MAX_ATTEMPTS`/`RATE_LIMIT_WINDOW_SECONDS` (default 30 requests/60s per client IP).

**Mechanism**: fixed-window, per-client-IP (`request.getRemoteAddr()`), in-memory (`ConcurrentHashMap`), one filter instance per protected path. On the `(maxAttempts + 1)`th request within the window, returns `429` with a `Retry-After` header and a JSON error body — no external service, no new dependency (deliberately not Bucket4j/Resilience4j; a fixed window is enough to make brute force economically pointless at this scale, per `RateLimitingFilter`'s own design javadoc).

**Why `FilterRegistrationBean`, not part of a `SecurityFilterChain`** (047-backend-hardening FR-004, transcribed from `RateLimitingFilter`'s own javadoc): registering it as a plain servlet `Filter` scoped to one exact URL pattern means it is never auto-registered as a container-wide filter and never auto-loaded into an unrelated `@WebMvcTest` slice (a real risk this codebase has hit before with other filters), and it never touches the already-delicate `SecurityConfig` bean-naming/ordering configuration described above at all — the two concerns (rate limiting, authentication) stay structurally independent.

**Known, accepted limitation**: in-memory and per-instance — correct for the current single-backend-instance deployment shape (see `PRODUCTION_ROADMAP.md`), not for a horizontally-scaled one, where a shared store (e.g. Redis) would be needed instead. Not a gap to close now — no horizontal scaling exists yet to make it wrong.

**Verified live** (047-backend-hardening, 2026-09-15): with `RATE_LIMIT_MAX_ATTEMPTS=3` set, 4 rapid `POST /api/v1/staff/login` requests against the real running application returned 401/401/401/429, confirming the filter actually engages end-to-end (not just in `RateLimitingFilterTest`'s own isolated unit test) — see `specs/047-backend-hardening/tasks.md` T016 for the full transcript.
