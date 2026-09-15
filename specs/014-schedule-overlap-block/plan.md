# Implementation Plan: Multi-Clinic Doctor Schedule Overlap Block

**Branch**: `014-schedule-overlap-block` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-schedule-overlap-block/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Adds one new repository query (`ScheduleRepository.findByDoctorProfile_Id`) and one new check step to 009's existing `ScheduleService.create()` — after validation and the staffing gate, before save, load every existing Schedule for the target doctor across all clinics and reject (`ScheduleOverlapException`, 409) if any shares a day-of-week and has an overlapping time range with the new submission. No new entity, no new migration, no new endpoint — a pure additive check on an existing, converged create path.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend, one small addition: a friendly error message for the new error code) — this feature extends 009's existing full-stack surface rather than introducing a new one.

**Primary Dependencies**: None new — reuses 009's `Schedule`/`ScheduleRepository`/`ScheduleService`/`ScheduleController`/`ScheduleExceptionHandler` directly.

**Storage**: PostgreSQL — no new migration. One new repository query over the existing `schedule`/`schedule_day` tables.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers + MockMvc (backend); one small addition to the existing frontend test (backend only, plus the error-message addition is trivial enough not to need its own new test file — see tasks.md).

**Target Platform**: Linux container (Docker).

**Project Type**: Extension of an existing backend service module (`com.cms.scheduling`), no new project structure.

**Performance Goals**: Same order of magnitude as prior features — one additional read query (a doctor's own Schedule count is small, no pagination needed) plus an in-memory O(n) overlap scan per create call.

**Constraints**:
- The overlap check MUST run against **every** clinic the doctor works at, not just the clinic named in the new submission (FR-001) — the query is `findByDoctorProfile_Id`, not scoped by clinic.
- The overlap predicate MUST be the strict half-open-interval definition (`newStart < existingEnd AND existingStart < newEnd`) so that exactly-touching ranges are never rejected (FR-003, spec AC2).
- No new configuration surface (a travel-time/gap setting) may be introduced (FR-006) — the check has no parameters beyond the two schedules being compared.

**Scale/Scope**: Single feature — 1 new repository method, 1 new exception, 1 new service check step, 1 new exception-handler mapping, 1 small frontend error-message addition. 0 new entities, 0 migrations, 0 new endpoints.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for every acceptance scenario (cross-clinic overlap, same-clinic overlap, touching-not-overlapping, disjoint days, full-containment, first-schedule-no-existing) written before implementation. |
| II. Simplicity & YAGNI | PASS | No travel-time/gap configuration added (explicitly rejected by the source material); the check is the smallest predicate that satisfies every acceptance scenario, added as a single step in an existing method rather than a new abstraction layer. |
| III. Modular, Library-First Architecture | PASS | Stays entirely within `com.cms.scheduling` — no new cross-module dependency, no new module. |
| IV. Data Privacy & Integrity by Design | PASS | Not identity/patient data; the check itself is a scheduling-integrity guarantee ("a doctor can never be scheduled to be in two places at once, at least on paper") evaluated fully in-transaction before any write. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/014-schedule-overlap-block/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── schedule-overlap.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/scheduling/
│   ├── ScheduleRepository.java          # extended: +findByDoctorProfile_Id
│   ├── ScheduleService.java             # extended: +overlap check step in create()
│   ├── ScheduleOverlapException.java    # new
│   └── ScheduleExceptionHandler.java    # extended: +handler for ScheduleOverlapException
└── src/test/java/com/cms/scheduling/integration/
    └── CreateScheduleOverlapTest.java   # new

frontend/
└── src/features/scheduling/api.ts       # extended: +SCHEDULE_OVERLAP error variant/message
```

**Structure Decision**: No new module, no new frontend feature folder — this is a pure extension of 009's existing `com.cms.scheduling` module and its existing `frontend/src/features/scheduling/` files, matching the "one additive check on an existing path" scope.

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The single-query, in-memory-scan design (data-model.md) confirms no unjustified complexity was introduced.

## Complexity Tracking

*No violations — table intentionally left empty.*
