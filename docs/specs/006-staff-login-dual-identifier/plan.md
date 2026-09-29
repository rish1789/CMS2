# Implementation Plan: Staff Login (Password or Staff Code)

**Branch**: `006-staff-login-dual-identifier` | **Date**: 2026-09-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-staff-login-dual-identifier/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Extend 004's existing `POST /api/v1/staff/login` to accept a staff code as an alternate identifier to email, resolving to the same Account either way, with identical failure-response shapes across both paths (no information leak). No new entity, no new endpoint — a targeted extension of existing code.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript + React 18 (frontend) — unchanged.

**Primary Dependencies**: None new — reuses 004's `StaffAuthController`, `AccountRepository`, `PasswordEncoder`, `StaffJwtService`.

**Storage**: PostgreSQL — no schema change; `account.staff_code` already exists (001/004).

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest + React Testing Library (frontend).

**Target Platform**: Linux container (Docker).

**Project Type**: Web application (backend + frontend).

**Performance Goals**: Unchanged.

**Constraints**:
- Staff-code login MUST produce a response indistinguishable in shape from the email path's equivalent success/failure cases (FR-004).

**Scale/Scope**: Single feature — 0 new entities, 0 new endpoints, 1 extended endpoint.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | New tests for the staff-code login path, written before the extension. |
| II. Simplicity & YAGNI | PASS | Smallest possible change — one field rename, one new repository method, a two-step lookup. No new endpoint, no speculative generality. |
| III. Modular, Library-First Architecture | PASS | Stays entirely within 004's existing `com.cms.identity.account` code. |
| IV. Data Privacy & Integrity by Design | PASS | No new credential data; the no-information-leak requirement (FR-004) is a direct continuation of 002/004's existing pattern. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/006-staff-login-dual-identifier/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/identity/account/
│   ├── AccountRepository.java         # + findByStaffCode
│   ├── StaffAuthController.java       # modified: identifier resolution
│   └── dto/StaffLoginRequest.java     # modified: email -> identifier
└── src/test/java/com/cms/identity/account/integration/

frontend/
└── src/features/staff-login/
    └── StaffLoginForm.tsx             # modified: label/field renamed to "Email or Staff Code"
```

**Structure Decision**: Pure extension of existing 004 files — no new packages, no new components.

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. No new violations.

## Complexity Tracking

*No violations — table intentionally left empty.*
