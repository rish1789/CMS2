---

description: "Task list for Multi-Clinic Doctor Schedule Overlap Block"
---

# Tasks: Multi-Clinic Doctor Schedule Overlap Block

**Input**: Design documents from `/specs/014-schedule-overlap-block/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/schedule-overlap.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Single user story (US1 = P1, the feature's entire scope) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 013's existing module: `backend/src/main/java/com/cms/scheduling/`, `backend/src/test/java/com/cms/scheduling/integration/`, `frontend/src/features/scheduling/`.

---

## Phase 1: Setup

- [X] T001 Create `ScheduleOverlapException` in `backend/src/main/java/com/cms/scheduling/ScheduleOverlapException.java`
- [X] T002 Add `findByDoctorProfile_Id(UUID doctorProfileId)` to `ScheduleRepository` in `backend/src/main/java/com/cms/scheduling/ScheduleRepository.java`

**Checkpoint**: New types/query exist; no behavior change yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: N/A beyond Setup — this feature extends an already-converged module's existing fixture (`AbstractScheduleIntegrationTest`, 013) directly, no new shared infrastructure needed.

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: User Story 1 - A Genuinely Overlapping Schedule Is Rejected, Anywhere the Doctor Works (Priority: P1) 🎯 MVP

**Goal**: `ScheduleService.create()` rejects (409 `SCHEDULE_OVERLAP`) any submission that shares a day and an overlapping time range with any of the doctor's existing Schedules, at any clinic; allows everything else.

**Independent Test**: Per quickstart.md Scenarios 1–5.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T003 [P] [US1] Integration test: cross-clinic overlap rejected (409 SCHEDULE_OVERLAP), same-clinic overlap rejected identically, in `backend/src/test/java/com/cms/scheduling/integration/CreateScheduleOverlapTest.java`
- [X] T004 [P] [US1] Integration test (same file as T003, additional `@Test` methods): touching ranges (end==start) allowed; disjoint days allowed regardless of time; full-containment rejected; a doctor's first Schedule (no existing ones) always allowed, in `backend/src/test/java/com/cms/scheduling/integration/CreateScheduleOverlapTest.java`
- [X] T005 [P] [US1] Integration test: a request that is both structurally invalid (per 013's own validation) and would overlap returns `400 INVALID_SCHEDULE`, not `409 SCHEDULE_OVERLAP` — confirms 013's existing error precedence is unchanged, in `backend/src/test/java/com/cms/scheduling/integration/CreateScheduleOverlapTest.java`

### Implementation for User Story 1

- [X] T006 [US1] Add the overlap-check step to `ScheduleService.create()` (after the staffing gate, before save) per data-model.md — in `backend/src/main/java/com/cms/scheduling/ScheduleService.java` (depends on T001, T002)
- [X] T007 [US1] Add `ScheduleOverlapException` → `409 SCHEDULE_OVERLAP` mapping to `ScheduleExceptionHandler` in `backend/src/main/java/com/cms/scheduling/ScheduleExceptionHandler.java` (depends on T001)
- [X] T008 [P] [US1] Add the `SCHEDULE_OVERLAP` error variant and a friendly default message to `frontend/src/features/scheduling/api.ts`'s `ScheduleApiErrorBody` union

**Checkpoint**: User Story 1 (the whole feature) fully functional and independently testable.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [X] T009 Run `quickstart.md` Scenarios 1–5 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T010 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 013's existing schedule tests

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: N/A (reuses 013's existing fixture).
- **User Story 1 (Phase 3)**: Depends on Setup.
- **Polish (Phase 4)**: Depends on User Story 1.

### Parallel Opportunities

- T001, T002 in parallel (different files).
- T003–T005 (all tests, same file but independent `@Test` methods) can be drafted in parallel by different authors, though they land in one file.
- T008 (frontend) is independent of T006/T007 (backend) — different files.

---

## Implementation Strategy

### MVP First (and Only) Story

1. Phase 1 → Phase 3. **STOP and VALIDATE**: quickstart.md Scenarios 1–5 pass.
2. Phase 4: full-suite verification and quickstart sign-off. This is the feature's entire scope.
