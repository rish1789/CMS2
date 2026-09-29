---

description: "Task list for Nightly Rolling Session Generation"
---

# Tasks: Nightly Rolling Session Generation (15-Day Horizon)

**Input**: Design documents from `/specs/015-nightly-session-generation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/session-generation.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 generation core, US2 = P2 manual Super Admin trigger) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 013/014's existing module: `backend/src/main/java/com/cms/scheduling/`, `backend/src/test/java/com/cms/scheduling/integration/`, new `frontend/src/features/session-generation/`, `frontend/tests/session-generation/`.

---

## Phase 1: Setup

**Purpose**: New entity/migration shared by both stories.

- [X] T001 Create migration `V8__create_session.sql` — `session` table + `UNIQUE (schedule_id, session_date)` constraint per data-model.md — in `backend/src/main/resources/db/migration/V8__create_session.sql`
- [X] T002 Create `Session` entity per data-model.md in `backend/src/main/java/com/cms/scheduling/Session.java` (depends on T001)

**Checkpoint**: Schema and entity exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Repository and shared test fixture both stories build on.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T003 Create `SessionRepository` (`findBySchedule_IdAndSessionDateIn`, plus a plain `findBySchedule_Id` for test assertions) in `backend/src/main/java/com/cms/scheduling/SessionRepository.java` (depends on T002)
- [X] T004 Create `AbstractSessionGenerationIntegrationTest.java` — Testcontainers base class extending/mirroring `AbstractScheduleIntegrationTest`'s builders, plus a Session-cleanup `@AfterEach` and a Super Admin Basic-Auth header helper (mirroring `AbstractAdminIntegrationTest`) — in `backend/src/test/java/com/cms/scheduling/integration/AbstractSessionGenerationIntegrationTest.java` (depends on T003)

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: The System Keeps a 15-Day Rolling Horizon of Sessions Populated (Priority: P1) 🎯 MVP

**Goal**: `SessionGenerationService.generate(LocalDate)` correctly, idempotently, and independently-per-schedule materializes Sessions with the right mode-appropriate snapshot.

**Independent Test**: Per quickstart.md Scenarios 1–4 — call `generate()` directly against seeded Schedules and assert on persisted Sessions.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T005 [P] [US1] Integration test: a Schedule with no existing Sessions gets exactly one Session per applicable date in the next 15 days, in `backend/src/test/java/com/cms/scheduling/integration/SessionGenerationFirstRunTest.java`
- [X] T006 [P] [US1] Integration test: calling `generate()` twice in a row (same or overlapping horizon) creates zero duplicate Sessions for any (schedule, date) pair, in `backend/src/test/java/com/cms/scheduling/integration/SessionGenerationNoDuplicateOnRepeatTest.java`
- [X] T007 [P] [US1] Integration test: two Schedules for two different doctors/clinics each get their own independently-correct Session set, in `backend/src/test/java/com/cms/scheduling/integration/SessionGenerationPerScheduleIndependenceTest.java`
- [X] T008 [P] [US1] Integration test: a Fixed-Time schedule's generated Sessions carry its `slotIntervalMinutes`; a Queue/Token schedule's generated Sessions carry none, in `backend/src/test/java/com/cms/scheduling/integration/SessionGenerationModeSnapshotTest.java`

### Implementation for User Story 1

- [X] T009 [US1] Implement `SessionGenerationService.generate(LocalDate runDate)` (plain orchestrator) and `.generateForSchedule(Schedule, LocalDate)` (`@Transactional`, own transaction per call) per data-model.md/research.md in `backend/src/main/java/com/cms/scheduling/SessionGenerationService.java` (depends on T003)

**Checkpoint**: User Story 1 fully functional and independently testable — generation is correct, idempotent, and per-schedule-isolated.

---

## Phase 4: Super Admin Manually Re-Triggers Generation (Priority: P2)

**Goal**: `POST /api/v1/admin/sessions/generate` runs the identical logic on demand, Super-Admin-only; the nightly `@Scheduled` job calls the same service.

**Independent Test**: Per quickstart.md Scenarios 5–7.

### Tests for User Story 2 (write first, confirm they FAIL before implementation)

- [X] T010 [P] [US2] Integration test: `POST /api/v1/admin/sessions/generate` with valid Super Admin credentials returns `200` with the correct `sessionsCreated` count and produces the same persisted result as calling the service directly; with no/invalid credentials or a staff bearer token instead → `401`, in `backend/src/test/java/com/cms/scheduling/integration/SessionGenerationManualTriggerAuthTest.java`

### Implementation for User Story 2

- [X] T011 [US2] Implement `SessionGenerationController` (`POST /api/v1/admin/sessions/generate`) per contracts/session-generation.md in `backend/src/main/java/com/cms/scheduling/SessionGenerationController.java` (depends on T009)
- [X] T012 [US2] Implement `SessionGenerationSchedulingConfig` (`@EnableScheduling`) and `NightlySessionGenerationTrigger` (`@Scheduled(cron = "0 0 2 * * *")`, calls `generate(LocalDate.now())`) in `backend/src/main/java/com/cms/scheduling/SessionGenerationSchedulingConfig.java` and `NightlySessionGenerationTrigger.java` (depends on T009)
- [X] T013 [P] [US2] Create `TriggerSessionGeneration.tsx` — Super Admin credentials form + trigger button, shows `sessionsCreated` on success — in `frontend/src/features/session-generation/TriggerSessionGeneration.tsx`
- [X] T014 [P] [US2] Create `api.ts` fetch client (`AdminCredentials`/Basic-Auth pattern per `clinic-verification/api.ts`) in `frontend/src/features/session-generation/api.ts`
- [X] T015 [US2] Frontend test: submits valid credentials, shows the created count; shows an error for invalid credentials — in `frontend/tests/session-generation/TriggerSessionGeneration.test.tsx` (depends on T013, T014)

**Checkpoint**: Both user stories independently functional — nightly and manual generation both work, Super-Admin-gated.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T016 Run `quickstart.md` Scenarios 1–7 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T017 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 009/010's existing schedule tests

---

## Phase 6: Convergence

- [X] T018 Fix `SessionGenerationService.generate()`: wrap each `generateForSchedule` call in a try/catch inside the loop (log and continue on failure) so one Schedule's failure never aborts generation for the remaining Schedules in the same run, per data-model.md's own documented design intent — in `backend/src/main/java/com/cms/scheduling/SessionGenerationService.java` per data-model.md, research.md (contradicts)

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational.
- **User Story 2 (Phase 4)**: Depends on US1's `SessionGenerationService` existing.
- **Polish (Phase 5)**: Depends on both stories.

### Parallel Opportunities

- T005–T008 (all US1 tests) in parallel — depend only on T004.
- T013, T014 in parallel within US2 implementation (different files).

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenarios 1–4 pass — generation is correct and idempotent, verifiable by calling the service directly.

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenarios 5–7 pass — both triggers work, Super-Admin-gated.
3. Phase 5: full-suite verification and quickstart sign-off.
