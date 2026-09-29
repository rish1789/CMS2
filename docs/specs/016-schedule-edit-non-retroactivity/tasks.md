---

description: "Task list for Schedule Edit Non-Retroactivity"
---

# Tasks: Schedule Edit Non-Retroactivity

**Input**: Design documents from `/specs/016-schedule-edit-non-retroactivity/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/schedule-edit.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Single user story (US1 = P1, the feature's entire scope) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 013/014/015's existing module: `backend/src/main/java/com/cms/scheduling/`, `backend/src/test/java/com/cms/scheduling/integration/`, `frontend/src/features/scheduling/`.

---

## Phase 1: Setup

- [X] T001 Add setters (`setDaysOfWeek`, `setStartTime`, `setEndTime`, `setMode`, `setSlotIntervalMinutes`) to `Schedule` in `backend/src/main/java/com/cms/scheduling/Schedule.java`
- [X] T002 Create `ScheduleNotFoundException` in `backend/src/main/java/com/cms/scheduling/ScheduleNotFoundException.java`

**Checkpoint**: New types exist; no behavior change yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: N/A beyond Setup — extends 013/014's already-converged fixture (`AbstractScheduleIntegrationTest`) directly.

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: Editing a Schedule Never Touches Already-Generated Sessions (Priority: P1) 🎯 MVP

**Goal**: `PATCH .../schedules/{scheduleId}` correctly re-validates, re-checks overlap (excluding itself), authorizes identically to create, and never touches any Session row.

**Independent Test**: Per quickstart.md Scenarios 1–4.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T003 [P] [US1] Integration test: editing a Schedule's time window leaves already-generated Sessions (via `SessionGenerationService`) completely unchanged; a subsequent generation run for a new date uses the edited window, in `backend/src/test/java/com/cms/scheduling/integration/EditScheduleTest.java`
- [X] T004 [P] [US1] Integration test (same file): every 009 validation rule re-applied on edit (QUEUE+interval, FIXED_TIME with no/non-positive interval, start≥end, empty days) rejected identically; a rejected edit leaves the Schedule's stored configuration unchanged, in `backend/src/test/java/com/cms/scheduling/integration/EditScheduleTest.java`
- [X] T005 [P] [US1] Integration test (same file): an edit creating a new overlap with another of the doctor's Schedules is rejected (409); an edit changing only the Schedule's own fields with no genuine new overlap is never rejected for overlapping itself, in `backend/src/test/java/com/cms/scheduling/integration/EditScheduleTest.java`
- [X] T006 [P] [US1] Integration test (same file): authorization matches 009's create rule (ClinicAdmin or the schedule's own doctor succeed; an unrelated staff member is 403); unknown clinic/doctor/schedule id, or a schedule id belonging to a different clinic/doctor, is 404, in `backend/src/test/java/com/cms/scheduling/integration/EditScheduleTest.java`

### Implementation for User Story 1

- [X] T007 [US1] Extract 013's `validate(CreateScheduleRequest)` as a shared method (no behavior change) and extend 014's `requireNoOverlap` with an optional exclude-schedule-id parameter, per research.md — in `backend/src/main/java/com/cms/scheduling/ScheduleService.java` (depends on T001)
- [X] T008 [US1] Implement `ScheduleService.edit(...)` per data-model.md (load → authorize → validate → overlap-check-excluding-self → apply setters → save; no `SessionRepository` reference anywhere in this method or its call chain) — in `backend/src/main/java/com/cms/scheduling/ScheduleService.java` (depends on T002, T007)
- [X] T009 [US1] Add `PATCH` mapping to `ScheduleController` per contracts/schedule-edit.md in `backend/src/main/java/com/cms/scheduling/ScheduleController.java` (depends on T008)
- [X] T010 [US1] Add `ScheduleNotFoundException` → `404 SCHEDULE_NOT_FOUND` mapping to `ScheduleExceptionHandler` in `backend/src/main/java/com/cms/scheduling/ScheduleExceptionHandler.java` (depends on T002)
- [X] T011 [P] [US1] Add `editSchedule` to `frontend/src/features/scheduling/api.ts` (same request/response shape as `createSchedule`, `PATCH` method)

**Checkpoint**: User Story 1 (the whole feature) fully functional and independently testable.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [X] T012 Run `quickstart.md` Scenarios 1–4 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T013 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 013/014/015's existing schedule/session tests

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: N/A (reuses 013/014's existing fixture).
- **User Story 1 (Phase 3)**: Depends on Setup.
- **Polish (Phase 4)**: Depends on User Story 1.

### Parallel Opportunities

- T001, T002 in parallel (different files).
- T003–T006 (all tests, same file but independent `@Test` methods) can be drafted in parallel.
- T011 (frontend) is independent of T007–T010 (backend).

---

## Implementation Strategy

### MVP First (and Only) Story

1. Phase 1 → Phase 3. **STOP and VALIDATE**: quickstart.md Scenarios 1–4 pass.
2. Phase 4: full-suite verification and quickstart sign-off. This is the feature's entire scope.
