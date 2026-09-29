# Implementation Plan: Schedule Edit Non-Retroactivity

**Branch**: `016-schedule-edit-non-retroactivity` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/016-schedule-edit-non-retroactivity/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Adds `PATCH /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules/{scheduleId}` to 013's existing `com.cms.scheduling` module — reusing 009's `CreateScheduleRequest` shape (full-payload replacement), the same authorization rule, the same field-validation logic (extracted to a shared method), and 010's overlap check extended with an "exclude this schedule from its own comparison" parameter. `Schedule` gains setters for its five editable fields. The edit path never touches `SessionRepository` at all — non-retroactivity (FR-006/FR-007) holds structurally, not by a runtime check, since no code path here can reach the `session` table.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend — extends 013's existing `ScheduleForm`/callers with an edit affordance).

**Primary Dependencies**: None new — reuses 009/010's `Schedule`/`ScheduleRepository`/`ScheduleService`/`ScheduleController`/`ScheduleExceptionHandler` directly.

**Storage**: PostgreSQL — no new migration. `Schedule`'s existing columns are updated in place; `schedule_day` (009's `@ElementCollection` join table) is replaced wholesale by Hibernate's standard collection-diffing on save when `daysOfWeek` changes.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers + MockMvc (backend), extending 013/014's existing test fixture. Frontend: extends 013's existing `ScheduleForm` test file.

**Target Platform**: Linux container (Docker).

**Project Type**: Extension of the existing `com.cms.scheduling` module and its existing frontend feature folder — no new project structure.

**Performance Goals**: Same order of magnitude as 009's create path.

**Constraints**:
- The edit MUST NOT construct, autowire, or call `SessionRepository`/`Session` anywhere in its code path (FR-006) — this is what makes non-retroactivity a structural guarantee, not a tested-but-fragile behavior.
- A rejected edit (validation, overlap, authorization, not-found) MUST leave the Schedule row's prior values completely unchanged (FR-005) — validation/overlap checks MUST run and pass *before* any field is mutated on the loaded entity.
- The overlap check MUST exclude the Schedule being edited from its own candidate set (FR-004) — reusing 010's existing predicate with one added `!existing.getId().equals(scheduleId)` filter.

**Scale/Scope**: Single feature — 5 new setters on `Schedule`, 1 new service method (`edit`), 1 new controller method, 1 new exception (`ScheduleNotFoundException`), 1 exception-handler mapping, small frontend addition.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for every FR (non-retroactivity of already-generated Sessions, future-generation picking up the edit, every 009 validation rule re-applied, self-exclusion in the overlap check, authorization, not-found) written before implementation. |
| II. Simplicity & YAGNI | PASS | No doctor/clinic-reassignment machinery invented (spec Scope Decisions); reuses 009/010's existing validation/overlap logic via extraction rather than duplicating it. |
| III. Modular, Library-First Architecture | PASS | Stays entirely inside `com.cms.scheduling`; no new cross-module dependency. |
| IV. Data Privacy & Integrity by Design | PASS | Non-retroactivity is itself a data-integrity guarantee, made structural (no code path to `Session` at all) rather than merely tested-for. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/016-schedule-edit-non-retroactivity/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── schedule-edit.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/scheduling/
│   ├── Schedule.java                    # extended: +5 setters
│   ├── ScheduleNotFoundException.java   # new
│   ├── ScheduleRepository.java          # extended: +findByDoctorProfile_Id already exists (014); no change needed
│   ├── ScheduleService.java             # extended: +edit(), validate()/requireNoOverlap() reused with an exclusion parameter
│   ├── ScheduleController.java          # extended: +PATCH mapping
│   └── ScheduleExceptionHandler.java    # extended: +ScheduleNotFoundException mapping
└── src/test/java/com/cms/scheduling/integration/
    └── EditScheduleTest.java            # new

frontend/
└── src/features/scheduling/
    └── api.ts                           # extended: +editSchedule client only — spec.md has no UI acceptance criterion; no ScheduleForm.tsx change in this feature (see tasks.md)
```

**Structure Decision**: Pure extension of 009/010's existing `com.cms.scheduling` module and `frontend/src/features/scheduling/` folder — no new module, no new frontend feature folder.

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The "no code path to Session" design (data-model.md) confirms Principle IV's non-retroactivity guarantee is structural in the detailed design, not just at the plan-summary level.

## Complexity Tracking

*No violations — table intentionally left empty.*
