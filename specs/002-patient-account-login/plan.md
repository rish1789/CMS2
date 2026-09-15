# Implementation Plan: Patient Account & Global Login

**Branch**: `002-patient-account-login` | **Date**: 2026-09-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-patient-account-login/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Implement self-service Patient Account signup and login: a global (not clinic-scoped) identity with email + password (policy-enforced) and an optional Indian-format mobile number, active immediately with no verification step. Entirely separate persistence and auth logic from staff Accounts (001) — same email may exist in both systems independently. Issues a JWT on login whose audience/scope is structurally incapable of satisfying staff-only authorization checks.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript + React 18 (frontend) — same as 001, no change.

**Primary Dependencies**: Spring Boot 3.x (Web, Data JPA, Validation, Security), `jjwt` (or Spring Security's OAuth2 resource-server JWT support) for token issuance/validation, Flyway, Gradle (backend); React 18, Tailwind CSS, Vite (frontend).

**Storage**: PostgreSQL — new `patient_account` table, no relationship to 001's `account`/`clinic`/`role_assignment` tables (see research.md's module-boundary decision).

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) for backend; Vitest + React Testing Library for frontend — same as 001.

**Target Platform**: Linux container (Docker) — same as 001.

**Project Type**: Web application (backend + frontend) — extends the existing `backend/`, `frontend/` structure from 001, new feature-scoped packages/folders within it.

**Performance Goals**: Signup/login complete and return a result within 2s at p95 under normal load — same order of magnitude as 001's registration endpoint; no different requirement stated.

**Constraints**:
- Patient Account is an intentional global (non-clinic-scoped) entity — explicitly named as such by the constitution's multi-tenancy section; queries against it MUST NOT apply clinic-scoping (there is no clinic to scope to).
- No shared table, entity, or authentication code path with staff Account (001) — structurally enforced via a separate package (`com.cms.patient.account`) and a separate DB table.
- Passwords hashed (BCrypt, same as 001) before storage; never logged or returned in plaintext.
- Patient Account email uniqueness is independent of staff Account email uniqueness — no cross-table uniqueness check between `patient_account.email` and `account.email`.
- Issued JWTs must carry an audience/scope claim that a staff-only endpoint's authorization check structurally rejects (FR-008).

**Scale/Scope**: Single feature — 1 new entity (Patient Account), 2 endpoints (signup, login).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests (unit for password/mobile validators — reused pattern from 001 — plus integration tests for signup/login/uniqueness) written before implementation, per Red-Green-Refactor. |
| II. Simplicity & YAGNI | PASS | No email verification, no password-reset flow, no lockout/rate-limiting — none required by spec, none added speculatively. Reuses 001's established stack rather than introducing new tooling. |
| III. Modular, Library-First Architecture | PASS | Patient Account is its own module (`com.cms.patient.account`) with a clear boundary from `com.cms.identity` — no reach-through, per research.md's module-boundary decision. |
| IV. Data Privacy & Integrity by Design | PASS | Password hashed (BCrypt). Email uniqueness enforced at the DB layer (not just app-level) to close the same concurrent-registration race class as 001's FR-012. Patient Account correctly treated as an explicit global exception to multi-tenant scoping, per the constitution's own carve-out. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/002-patient-account-login/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Extends the existing `backend/`, `frontend/` layout from 001 — no new top-level projects.

```text
backend/
├── src/main/java/com/cms/patient/account/
│   ├── PatientAccount.java              # Entity
│   ├── PatientAccountRepository.java
│   ├── PatientAccountService.java       # Signup + login/authenticate
│   ├── JwtService.java                  # Token issuance/validation, patient-scoped
│   └── SecurityConfig.java              # Patient-facing auth filter chain
├── src/main/java/com/cms/patient/api/
│   ├── PatientAccountController.java    # POST /signup, POST /login
│   └── dto/
├── src/main/resources/db/migration/
│   └── V2__create_patient_account.sql
└── src/test/java/com/cms/patient/
    ├── account/                          # Unit tests (password/mobile validators reused from 001, JWT scope test)
    └── integration/                      # Testcontainers integration tests

frontend/
├── src/features/patient-account/
│   ├── SignupForm.tsx
│   ├── LoginForm.tsx
│   └── api.ts
└── tests/
    └── patient-account/
```

**Structure Decision**: Same web-application split as 001. This feature adds sibling packages (`com.cms.patient.*`) alongside 001's `com.cms.identity.*`, keeping the two identity systems structurally separate per FR-004/FR-008.

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The data model's DB-level unique constraint on `patient_account.email` and the JWT audience-scoping decision in research.md directly implement Principle IV and FR-008 — no new violations introduced by the detailed design.

## Complexity Tracking

*No violations — table intentionally left empty.*
