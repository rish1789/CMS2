# Implementation Plan: Clinic Registration

**Branch**: `001-clinic-registration` | **Date**: 2026-09-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-clinic-registration/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Implement clinic registration: a public endpoint that atomically creates a Clinic (unverified by default) together with its founding ClinicAdmin's Account and Role Assignment, in one transaction. Enforces password policy, optional Indian-format mobile number validation, and platform-wide-unique ClinicAdmin email and staff code (both closed at the database layer to prevent duplicate-creation races). No Grievance Officer, billing, or file-upload fields exist anywhere in the flow. Rolls back wholesale — leaving no orphaned Clinic — if any part of creation fails.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript + React 18 (frontend)

**Primary Dependencies**: Spring Boot 3.x (Web, Data JPA, Validation, Security), Flyway, Gradle (backend); React 18, Tailwind CSS, Vite (frontend)

**Storage**: PostgreSQL

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) for backend; Vitest + React Testing Library for frontend

**Target Platform**: Linux container (Docker)

**Project Type**: Web application (backend + frontend)

**Performance Goals**: Registration submission completes and returns a result within 2s at p95 under normal load — a low-frequency, admin-facing operation with no stated high-throughput requirement.

**Constraints**:
- Multi-tenant scoping enforced at the query layer per constitution (though this feature's own entities — Clinic, Account, Role Assignment — are the tenant roots, not tenant-scoped data themselves).
- ClinicAdmin email and staff code MUST be enforced unique at the database layer (unique constraints/indexes), not only in application code, to close concurrent-registration races (Constitution Principle IV).
- Passwords MUST be hashed (BCrypt) before storage; never logged or returned in plaintext.
- Clinic + Account + Role Assignment creation MUST occur inside a single atomic database transaction with full rollback on any failure.

**Scale/Scope**: Single feature — 3 new entities (Clinic, Account, Role Assignment), 1 primary endpoint (clinic + founding-admin registration).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Integration tests (Testcontainers) must be written first, proving: (a) successful atomic creation, (b) full rollback with no orphaned Clinic on partial failure, (c) duplicate email/staff-code rejected at the DB layer. Tasks phase will sequence tests before implementation per Red-Green-Refactor. |
| II. Simplicity & YAGNI | PASS | No billing, file-upload, or configurable-retention scaffolding added — matches confirmed v1 scope exactly. No speculative abstraction beyond a single Clinic-registration service. |
| III. Modular, Library-First Architecture | PASS | Registration logic lives in a dedicated Identity/Clinic module with its own service and REST contract; no reach-through into other modules (e.g. discovery, scheduling) — they will read Clinic.verified later via their own scoped queries, not through this module's internals. |
| IV. Data Privacy & Integrity by Design | PASS | Email and staff-code uniqueness closed at the DB layer (race-safety). Passwords hashed (BCrypt). This feature creates no patient/clinical data, so DPDP anonymization/retention rules do not apply here. |

No violations — Complexity Tracking is not needed.

*(Re-checked post-Phase 1 design below — see "Post-Design Constitution Re-Check".)*

## Project Structure

### Documentation (this feature)

```text
specs/001-clinic-registration/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

This is the first feature planned in the project, so this establishes the project's baseline source layout (Option 2: web application, backend + frontend split, per the constitution's declared stack):

```text
backend/
├── src/main/java/com/cms/identity/
│   ├── clinic/            # Clinic entity, repository, service
│   ├── account/           # Account entity, repository, service, PasswordEncoder config
│   └── api/                # ClinicRegistrationController, request/response DTOs
├── src/main/resources/db/migration/
│   └── V1__create_clinic_account_role_assignment.sql
└── src/test/java/com/cms/identity/
    ├── clinic/             # Unit tests
    └── integration/        # Testcontainers integration tests (atomicity, uniqueness races)

frontend/
├── src/features/clinic-registration/
│   ├── RegistrationForm.tsx
│   └── api.ts
├── src/components/         # Shared Tailwind UI primitives
└── tests/
    └── clinic-registration/
```

**Structure Decision**: Web application split (`backend/`, `frontend/`), matching the constitution's declared Spring Boot backend + Tailwind-styled frontend. As the first feature, this directory layout is the project's baseline — later features add sibling packages/feature folders within the same two roots rather than introducing new top-level projects.

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The data model's DB-level unique constraints (data-model.md) and the contract's explicit error responses for duplicate email/invalid password/invalid mobile (contracts/) directly implement the Principle IV and Principle I requirements noted above — no new violations introduced by the detailed design.

## Complexity Tracking

*No violations — table intentionally left empty.*
