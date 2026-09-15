# Implementation Plan: Last Active ClinicAdmin Protection

**Branch**: `005-last-active-clinicadmin-protection` | **Date**: 2026-09-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-last-active-clinicadmin-protection/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

A ClinicAdmin deactivates a Role Assignment (Doctor, Operations, or ClinicAdmin) at their own clinic. Deactivating a ClinicAdmin Role Assignment is blocked, with no override for any role, if it would leave the clinic with zero active ClinicAdmins. Idempotent. Reuses 004's staff JWT authentication and authorization pattern entirely.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript + React 18 (frontend) — same as prior features.

**Primary Dependencies**: Spring Boot 3.x — reuses 004's `StaffJwtService`, `RoleAssignmentRepository`, `AccountRepository`. No new dependency.

**Storage**: PostgreSQL — no new table, no schema change. Writes `role_assignment.active` only (already exists per 001's migration).

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest + React Testing Library (frontend).

**Target Platform**: Linux container (Docker).

**Project Type**: Web application (backend + frontend).

**Performance Goals**: Same order of magnitude as prior features.

**Constraints**:
- The last-active-ClinicAdmin check MUST run at the service/API layer, not only in a UI, and MUST have no override path for any role (FR-004, FR-006).
- Deactivation MUST be idempotent (FR-007).
- The check MUST be clinic-scoped (FR-008) — a single DB query naturally scoped to the target clinic.

**Scale/Scope**: Single feature — 0 new entities, 1 new endpoint.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for the protection invariant (including the "no Super Admin override" case) and idempotency written before implementation. |
| II. Simplicity & YAGNI | PASS | No new entity, no new module — reuses 004's authentication/authorization pattern directly. A single count query implements the protection check; no speculative generality. |
| III. Modular, Library-First Architecture | PASS | Placed alongside 004's onboarding service in `com.cms.identity.staff` — the natural counterpart, not a new boundary. |
| IV. Data Privacy & Integrity by Design | PASS | This *is* an integrity invariant by design — the entire feature exists to enforce one. No credential/identity data introduced. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/005-last-active-clinicadmin-protection/
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
├── src/main/java/com/cms/identity/staff/
│   ├── StaffDeactivationService.java
│   ├── StaffDeactivationController.java
│   └── LastActiveClinicAdminException.java
└── src/test/java/com/cms/identity/staff/integration/

frontend/
├── src/features/staff-onboarding/    # extended, not a new feature folder
│   └── DeactivateStaffAction.tsx
└── tests/
    └── staff-onboarding/
```

**Structure Decision**: New service/controller alongside 004's in `com.cms.identity.staff`; frontend adds a component to the existing `staff-onboarding` feature folder rather than a new one, since it's the natural "manage staff" counterpart to onboarding.

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. No new violations.

## Complexity Tracking

*No violations — table intentionally left empty.*
