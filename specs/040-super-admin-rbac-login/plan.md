# Implementation Plan: Super Admin RBAC Login & Console Access

**Branch**: `040-super-admin-rbac-login` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/040-super-admin-rbac-login/spec.md`

## Summary

Give Super Admin a real login step and JWT session (replacing today's per-request HTTP Basic Auth), delivered through a single combined **Clinic Portal** login shared with staff (ClinicAdmin/Doctor/Operations) — the existing `/staff/login` screen and `POST /api/v1/staff/login` endpoint, extended rather than replaced. On successful login, the system resolves whether the credential belongs to the configured Super Admin or a staff Account and redirects accordingly (`/super-admin-console` vs the existing `/staff` dashboard). The Patient Portal is untouched. A new frontend route guard protects `/super-admin-console` (hosting the existing, previously-unguarded `/admin/*` screens, moved under this new prefix), and the backend's `/api/v1/admin/**` chain switches from Basic Auth to bearer-JWT verification with its own `SUPER_ADMIN` audience, mirroring the staff/patient JWT pattern already established in this codebase.

## Technical Context

**Language/Version**: Java 21 (backend, Spring Boot), TypeScript/React (frontend) — unchanged, this feature extends the existing stack established by `001`.

**Primary Dependencies**: Spring Security (filter chains, already in use), `io.jsonwebtoken` (JJWT, already used by `StaffJwtService`/patient `JwtService` — reused, no new dependency), React Router (`react-router-dom`, already in use for route guards).

**Storage**: PostgreSQL via Flyway-managed schema — **no new migration**; this feature adds no persisted entity (data-model.md).

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend, existing pattern across all 39 prior features — same sandbox Docker/docker-java limitation applies, tests will be written/compiling but unexecuted here per every prior feature's documented precedent); Vitest + React Testing Library (frontend, existing pattern).

**Target Platform**: Same as the rest of the system — Spring Boot server + browser SPA.

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: No new performance requirement beyond existing login/auth-check latency norms already met by the staff/patient login and JWT-validation paths this feature mirrors.

**Constraints**: Must not change Patient Portal behavior at all (FR-002/FR-007). Must not change any existing staff login *outcome* (FR-006/SC-004) beyond the additive `role` response field. Must not introduce a second Super Admin credential store (spec Assumption) — the single config-bootstrapped credential pair from `002` is unchanged.

**Scale/Scope**: One backend endpoint extended (not created), one new backend JWT service + filter + entry point (mirroring existing staff/patient equivalents), one Spring Security filter chain modified (Basic Auth → bearer JWT), one new frontend route guard, three existing frontend admin screens + their `api.ts` files updated from inline Basic Auth to a stored bearer token, one frontend route tree change (`/admin` → guarded `/super-admin-console`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design below.*

- **I. Test-First Development**: PASS (planned) — every new/changed behavior (Super-Admin-vs-staff resolution order, new JWT issuance/validation, route guard, updated admin screens) gets a failing test first per this codebase's established pattern; no Flyway migration exists here to test separately.
- **II. Simplicity & YAGNI**: PASS — deliberately reuses the existing staff login endpoint/route (R1) instead of a new one, reuses the existing `superAdminUserDetailsService` bean instead of a new credential store, and avoids a discriminated-union DTO or a cross-portal "role router" abstraction in favor of one additive field and one small pure function (R4, R5). No speculative extensibility introduced.
- **III. Modular, Library-First Architecture**: PASS — no new module boundary needed; changes land inside the two modules that already own this behavior (`com.cms.identity.account` for login/JWT, `com.cms.identity.admin` for the Super Admin filter chain), exactly where `002`'s own `SuperAdminSecurityConfig` and `004`'s `StaffAuthController` already live. The one place this feature crosses that boundary — `StaffAuthController` needing to check a Super Admin credential — goes through one new exposed contract, `SuperAdminAuthenticationService.authenticate(identifier, password)` (research.md R2), not a direct injection of `com.cms.identity.admin`'s internal `UserDetailsService`/JWT-signing beans. No new cross-module event needed (this is a synchronous auth concern, not an async side effect Principle III's event-driven requirement targets).
- **IV. Data Privacy & Integrity by Design**: PASS / N/A — no patient-identifying or clinical data involved; no anonymization/retention concern. The one integrity-relevant point (FR-004's "don't leak which check failed") is enforced structurally by reusing the single existing `InvalidCredentialsException` for every non-match path (research.md R2), not by application-level string matching that could drift.
- **Technology & Platform Constraints**: PASS — no new migration, no new payment/upload/global-record scope creep; multi-tenancy is not implicated (Super Admin is one of the constitution's own named global-entity exceptions).

No violations. Complexity Tracking table omitted (nothing to justify).

## Project Structure

### Documentation (this feature)

```text
specs/040-super-admin-rbac-login/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── clinic-portal-login.md
└── tasks.md             # Phase 2 output (/speckit-tasks — not created by this command)
```

### Source Code (repository root)

```text
backend/
└── src/main/java/com/cms/identity/
    ├── account/
    │   ├── StaffAuthController.java          # MODIFIED: tries SuperAdminAuthenticationService.authenticate() first, then existing Account lookup
    │   ├── SecurityConfig.java                # unchanged (staff chain itself is not touched)
    │   ├── StaffJwtService.java               # unchanged (reused as-is for the staff path)
    │   └── dto/
    │       └── StaffLoginResponse.java        # MODIFIED: + `role` field, `accountId` now nullable
    └── admin/
        ├── SuperAdminSecurityConfig.java       # MODIFIED: httpBasic() → bearer JWT filter chain
        ├── SuperAdminJwtService.java            # NEW: mirrors StaffJwtService, audience SUPER_ADMIN
        ├── SuperAdminAuthenticationService.java  # NEW: the one contract com.cms.identity.account calls (research.md R2) — composes superAdminUserDetailsService + SuperAdminJwtService internally, keeping both encapsulated in this module
        ├── SuperAdminJwtAuthenticationFilter.java # NEW: mirrors StaffJwtAuthenticationFilter
        └── SuperAdminAuthenticationEntryPoint.java # NEW: mirrors StaffAuthenticationEntryPoint
backend/src/main/resources/application.yml       # MODIFIED: + admin.super-admin.jwt.secret
backend/src/test/java/com/cms/identity/
    ├── account/integration/StaffLoginTest.java           # MODIFIED: + role field assertions (STAFF)
    ├── account/integration/                              # NEW: Super-Admin-resolution login test(s)
    └── admin/integration/
        ├── AbstractAdminIntegrationTest.java             # MODIFIED: superAdminAuthHeader() issues a JWT, not Basic Auth (research.md R9) — landed together with the SuperAdminSecurityConfig chain switch (same task), not deferred, since ~15+ pre-existing test classes in this package depend on this one helper to authenticate successfully
        └── AdminAuthorizationTest.java                    # MODIFIED: staff-rejection cases use a staff JWT, not Basic Auth; + new "Basic Auth no longer accepted" test

frontend/
└── src/
    ├── routes/
    │   ├── guards.tsx                          # MODIFIED: + RequireSuperAdminSession
    │   └── staff/StaffLoginPage.tsx            # MODIFIED: role-based redirect via decideClinicPortalDestination
    ├── features/
    │   ├── staff-login/
    │   │   ├── StaffLoginForm.tsx              # MODIFIED: branches on response.role
    │   │   ├── api.ts                          # MODIFIED: LoginStaffResponse + role field
    │   │   └── destination.ts                  # NEW: decideClinicPortalDestination (research.md R5)
    │   ├── super-admin/
    │   │   └── token.ts                        # NEW: loadSuperAdminSession/storeSuperAdminSession (mirrors staff-login/token.ts)
    │   ├── clinic-verification/
    │   │   ├── api.ts                          # MODIFIED: AdminCredentials/Basic Auth → bearer token
    │   │   └── PendingClinicsList.tsx           # MODIFIED: remove inline login form, use stored session
    │   ├── doctor-verification/
    │   │   ├── api.ts                          # MODIFIED: same as clinic-verification
    │   │   └── PendingDoctorsList.tsx           # MODIFIED: same as clinic-verification
    │   └── session-generation/
    │       ├── api.ts                          # MODIFIED: same as clinic-verification
    │       └── TriggerSessionGeneration.tsx     # MODIFIED: same as clinic-verification
    └── App.tsx                                  # MODIFIED: /admin route → guarded /super-admin-console
frontend/tests/                                   # MODIFIED/NEW: mirrors the above (staff-login role branching, guard, updated admin screens)
```

**Structure Decision**: Existing `backend/` (Spring Boot, Gradle) + `frontend/` (Vite/React) web-application layout, unchanged. This feature adds no new top-level module — it extends `com.cms.identity.account` (login) and `com.cms.identity.admin` (Super Admin's own package, established by `002`) on the backend, and adds one new frontend feature folder (`features/super-admin/`, session storage only) alongside modifying the existing `staff-login` and three admin-screen feature folders.
