# Implementation Plan: Recurring Schedule Definition

**Branch**: `013-recurring-schedule-definition` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-recurring-schedule-definition/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

A new `com.cms.scheduling` module (the "Scheduling & Session Generation" bounded context Constitution III names explicitly) defining the `Schedule` entity (days-of-week, time range, mode, optional slot interval) and two endpoints under the existing `/api/v1/clinics/**` security chain: `POST .../doctors/{doctorProfileId}/schedules` (create) and `GET .../doctors/{doctorProfileId}/schedules` (list). Authorization (that clinic's active ClinicAdmin, or the named doctor themselves) and the active-Role-Assignment staffing gate both reuse `RoleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue` (004's existing method) directly — no new authorization mechanism. No overlap check, no edit endpoint, no Session/Slot entity (all explicitly deferred to 010/014/011). Ships with a staff-facing form (`frontend/src/features/scheduling/`), matching every other staff/admin-facing capability with a real, already-logged-in caller in this codebase (`OnboardStaffForm`, `PendingDoctorsList`, etc.) — unlike 009's/011's service-only precedent, which applied specifically because *no* caller existed yet.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — this feature has both. Its two callers (ClinicAdmin, Doctor) are real, already-authenticated actors in this system today, unlike 009/011's service-only precedent (built for consumers that didn't exist yet).

**Primary Dependencies**: Spring Boot 3.x (Data JPA, Web, Security) — reuses `com.cms.identity.clinic.Clinic` (001), `com.cms.identity.doctor.DoctorProfile` (005), `com.cms.identity.account.RoleAssignment`/`RoleAssignmentRepository` (004) directly. Frontend reuses the existing React + Tailwind stack and the staff-JWT-bearer fetch pattern already established by `staff-onboarding`'s `OnboardStaffForm`. No new external dependency either side.

**Storage**: PostgreSQL — one new migration (`V7`): `schedule` table plus a `schedule_day` element-collection join table (a `Schedule` has one-to-many days-of-week).

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers + MockMvc (backend only).

**Target Platform**: Linux container (Docker).

**Project Type**: Backend service module with two REST endpoints (no frontend in this feature).

**Performance Goals**: Same order of magnitude as prior features (2s p95) — simple validated inserts/reads.

**Constraints**:
- The create endpoint MUST reject (400) a time range, day-set, or mode/slot-interval combination that violates FR-005–FR-008 before any write — no partial Schedule is ever persisted (SC-002).
- Authorization (FR-001–FR-003) MUST be checked before any validation error is revealed, mirroring 004's existing `StaffOnboardingService.onboard()` ordering (check caller's standing first).
- No write path in this feature touches any Session/Slot table or entity — none exists yet (FR-011, SC-004).

**Scale/Scope**: Single feature — 1 new entity (`Schedule`) + 1 new enum (`ScheduleMode`), 1 migration, 1 repository, 1 service, 1 controller, 2 endpoints, 0 frontend changes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for every FR (both authorized-actor paths, the forbidden path, every validation rule, the doctor-clinic staffing gate, list retrieval, and the zero-Session/Slot guarantee) written before implementation. |
| II. Simplicity & YAGNI | PASS | No overlap check, no edit endpoint, no Session/Slot machinery — strictly the create+list scope build-order.md and this feature's own spec assign to it. Reuses 004's existing authorization-check method rather than inventing a new one. |
| III. Modular, Library-First Architecture | PASS | New `com.cms.scheduling` module — Constitution's own named example of a module boundary. Reads `Clinic`/`DoctorProfile`/`RoleAssignment` as read-only cross-module references, the established pattern. |
| IV. Data Privacy & Integrity by Design | PASS | Not patient-identifying data. The doctor-clinic staffing gate (FR-009) is itself a data-integrity guarantee, checked at write time against the live `RoleAssignment` state. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/013-recurring-schedule-definition/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── schedule.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/scheduling/
│   ├── Schedule.java                          # new entity
│   ├── ScheduleMode.java                       # new enum (FIXED_TIME, QUEUE)
│   ├── ScheduleRepository.java                 # new
│   ├── ScheduleService.java                    # new
│   ├── ScheduleController.java                 # new
│   ├── ScheduleExceptionHandler.java           # new
│   ├── ClinicNotFoundException.java            # new (local, mirrors identity.staff's pattern)
│   ├── DoctorProfileNotFoundException.java     # new (local)
│   ├── DoctorNotStaffedAtClinicException.java  # new
│   ├── InvalidScheduleException.java           # new — the shared 400 for FR-005/006/007/008 violations
│   ├── ForbiddenException.java                 # new (local, mirrors identity.staff's)
│   └── dto/
│       ├── CreateScheduleRequest.java
│       └── ScheduleResponse.java
├── src/main/java/com/cms/identity/account/SecurityConfig.java   # extended: 2 new matchers on the existing @Order(1) chain
├── src/main/resources/db/migration/
│   └── V7__create_schedule.sql                 # new
└── src/test/java/com/cms/scheduling/integration/
    ├── AbstractScheduleIntegrationTest.java
    ├── CreateScheduleClinicAdminTest.java
    ├── CreateScheduleDoctorSelfTest.java
    ├── CreateScheduleForbiddenTest.java
    ├── CreateScheduleValidationTest.java
    ├── CreateScheduleStaffingGateTest.java
    └── ListSchedulesTest.java

frontend/
├── src/features/scheduling/
│   ├── ScheduleForm.tsx               # new — create form (days, time range, mode, slot interval)
│   └── api.ts                         # new — fetch client for POST/GET .../schedules, bearer-token pattern per OnboardStaffForm
└── tests/scheduling/
    └── ScheduleForm.test.tsx          # new
```

**Structure Decision**: A new top-level `com.cms.scheduling` backend module — Constitution III's own worked example of a module boundary ("scheduling/session generation, booking, waitlist, clinical documentation, notifications, discovery"). The two new endpoints extend the *existing* `/api/v1/clinics/**` security chain (`com.cms.identity.account.SecurityConfig`, `@Order(1)`) with two new explicit matchers, exactly like 004/005 did — not a new chain, since the path genuinely falls under that existing prefix. A new `frontend/src/features/scheduling/` folder follows the existing one-folder-per-feature convention, with the token passed explicitly into the API client exactly like `onboardStaff`/`deactivateStaff` (the caller, not this feature, owns where the staff JWT is stored).

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The validation-before-write, authorization-before-validation ordering (data-model.md) confirms Principle IV's integrity requirement holds in the detailed design.

## Complexity Tracking

*No violations — table intentionally left empty.*
