# 042 — Backend Module Layering & Security Posture Documentation

**Module:** Cross-Cutting / Backend Architecture
**Status:** Ready for spec-kit intake

## User Story
As a backend developer working in CMS2, I want each module's internal files organized by architectural layer (controllers, services, repositories, entities, exceptions) instead of as one flat package, and a single document indexing the system's security posture, so that I can find "the service that owns X" by browsing a directory instead of scanning filenames by suffix, and understand the full authentication/authorization surface without grepping five separate files.

## Context
The backend already follows package-per-feature (`com.cms.booking`, `com.cms.scheduling`, etc.), which is correct per the constitution's Principle III and should not change. The problem is *inconsistent internal layering within* each module. Verified current state (2026-09-15): `com.cms.booking` has 66 files as flat siblings (controllers, services, entities, ~18 exception classes) with only `dto/` broken into a subpackage; `com.cms.scheduling` (37 files) and `com.cms.clinical` (19 files) follow the same flat pattern. Security configuration is scattered across 5 independent classes with no central index: `patient/account/SecurityConfig.java`, `identity/account/SecurityConfig.java`, `identity/admin/SuperAdminSecurityConfig.java`, `booking/BookingSecurityConfig.java`, `discovery/DiscoverySecurityConfig.java`.

This is explicitly a mechanical, behavior-preserving refactor — package/file moves and a new doc — not a rewrite. The existing (converged, tested) business logic in every module is the correctness baseline; this feature must not change what any module does, only where its files live and how its security posture is documented.

## Business Rules
- Target internal structure per module (matching `PRODUCTION_ROADMAP.md` §2): `api/` (controllers + request/response HTTP concerns), `dto/` (already exists — keep as-is), `service/` (business logic, no HTTP/JPA leakage into callers), `repository/` (Spring Data interfaces only), `domain/` (JPA entities + enums), `exception/` (module-specific exceptions + the module's `@ExceptionHandler`), `config/` (e.g. `BookingSecurityConfig`).
- Apply this shape to every backend module with more than a handful of files, not just `booking`/`scheduling`/`clinical` — audit all of `com.cms.*` at planning time and enumerate exactly which modules need reorganizing vs. which are already small enough to leave flat (a module with 3-4 files doesn't need five subpackages).
- This MUST be executed one module at a time, running the full existing test suite after each module's move, since a clean pass after each move is the actual proof the move was mechanical and introduced no behavior change — this is the test-first principle applied in reverse (tests-already-exist-first, prove they still pass after each atomic move).
- `spring-boot-starter-actuator` MUST be added with only `/actuator/health` exposed publicly; every other actuator endpoint MUST be explicitly denied (`never` exposure) in the relevant Spring Security configuration — this is a new capability, not a refactor, so it needs its own test proving non-health actuator endpoints are unreachable.
- The new security posture document (`SECURITY.md` or `docs/security-posture.md`) is documentation only — it must accurately describe the 5 existing `SecurityConfig` classes' actual current filter-chain scope (paths, auth mechanism, public vs. authenticated) without changing any of their behavior. Treat any behavioral gap discovered while writing it (e.g., an unintentionally-public path) as a separate, explicitly-flagged finding — not something to silently fix inside a "just documentation" feature.

## Acceptance Criteria
- Given the full backend test suite (`gradle test`) passing before this feature starts, when each module's file reorganization is complete, then the full test suite still passes with zero test file content changes beyond updated package declarations/imports.
- Given a request to `GET /actuator/health` after this feature, when the backend is running, then it returns a 200 health status with no authentication required.
- Given a request to any other `/actuator/**` endpoint (e.g. `/actuator/env`, `/actuator/beans`), when attempted, then it is rejected (404 or 403, not exposing internal application state).
- Given the new security posture document, when a reviewer reads it, then it lists all 5 `SecurityConfig` classes, the path pattern(s) each governs, and which JWT realm (patient/staff/super-admin) or public/permitAll status applies to each — verifiable against the actual current `SecurityFilterChain` bean configurations.
- Given `git diff --stat` after this feature (excluding the new actuator dependency and SECURITY.md), when reviewed, then every changed file is a rename/move with package-declaration and import updates only — no logic lines changed.

## Dependencies
- None of the 39 converged features block this — it's a structural refactor of already-shipped code.
- Should happen before or alongside 043 (frontend API client) as part of the same "safety net before UI redesign" phase, per the user's chosen sequencing (roadmap/hardening before UI work).

## Explicitly Out of Scope
- Any change to business logic, validation rules, or API contracts (request/response shapes, status codes) in any module.
- Consolidating the 5 `SecurityConfig` classes into fewer files — the roadmap explicitly recommends documenting the existing 5-chain structure, not restructuring it, since each chain intentionally scopes a different JWT realm.
- Actuator metrics/Prometheus integration — only `/health` is in scope for this pass.

## Source References
- `PRODUCTION_ROADMAP.md` §1.2, §2 (Target Architecture — Backend), §3 Phase 3
- `.specify/memory/constitution.md` Principle III (Modular, Library-First Architecture)
- Verified against current repository state via direct inspection, 2026-09-15 (66/37/19 flat files confirmed in booking/scheduling/clinical; 5 SecurityConfig classes confirmed; no actuator dependency found)
