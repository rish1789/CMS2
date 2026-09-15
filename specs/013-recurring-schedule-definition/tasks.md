---

description: "Task list for Recurring Schedule Definition"
---

# Tasks: Recurring Schedule Definition

**Input**: Design documents from `/specs/013-recurring-schedule-definition/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/schedule.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 ClinicAdmin creates/lists, US2 = P2 Doctor self-service) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Web app split per plan.md: `backend/src/main/java/com/cms/scheduling/`, `backend/src/test/java/com/cms/scheduling/integration/`, `frontend/src/features/scheduling/`, `frontend/tests/scheduling/`.

---

## Phase 1: Setup

**Purpose**: New module scaffolding — entity, enum, DTOs, exceptions.

- [X] T001 [P] Create `ScheduleMode` enum (`FIXED_TIME`, `QUEUE`) in `backend/src/main/java/com/cms/scheduling/ScheduleMode.java`
- [X] T002 [P] Create exceptions `ClinicNotFoundException`, `DoctorProfileNotFoundException`, `DoctorNotStaffedAtClinicException`, `InvalidScheduleException`, `ForbiddenException` in `backend/src/main/java/com/cms/scheduling/`
- [X] T003 [P] Create `CreateScheduleRequest`/`ScheduleResponse` DTOs per contracts/schedule.md in `backend/src/main/java/com/cms/scheduling/dto/`
- [X] T004 Create migration `V7__create_schedule.sql` — `schedule` table + `schedule_day` element-collection join table per data-model.md — in `backend/src/main/resources/db/migration/V7__create_schedule.sql`
- [X] T005 Create `Schedule` entity per data-model.md in `backend/src/main/java/com/cms/scheduling/Schedule.java` (depends on T001, T004)

**Checkpoint**: Schema and types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Repository, security wiring, and shared test fixture both stories build on.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T006 Create `ScheduleRepository` (`findByClinic_IdAndDoctorProfile_Id`) in `backend/src/main/java/com/cms/scheduling/ScheduleRepository.java` (depends on T005)
- [X] T007 Extend `com.cms.identity.account.SecurityConfig`'s existing `@Order(1)` chain — add explicit `.authenticated()` matchers for `POST`/`GET /api/v1/clinics/*/doctors/*/schedules`, mirroring 004/005's existing matcher additions — in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`
- [X] T008 Create `AbstractScheduleIntegrationTest.java` — Testcontainers + MockMvc base class with helper builders for a verified `Clinic`, a `DoctorProfile` + active/inactive Role Assignment, a ClinicAdmin `Account` + Role Assignment, and staff-JWT header helpers (mirroring `AbstractStaffIntegrationTest`/`AbstractAdminIntegrationTest`'s patterns) — in `backend/src/test/java/com/cms/scheduling/integration/AbstractScheduleIntegrationTest.java` (depends on T006, T007)

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: User Story 1 - ClinicAdmin Defines a Doctor's Recurring Schedule (Priority: P1) 🎯 MVP

**Goal**: `POST`/`GET .../schedules` work end-to-end for a ClinicAdmin, with every validation rule, the staffing gate, and the forbidden-caller rejection enforced.

**Independent Test**: Per quickstart.md Scenarios 1–3, 5, 6 — a ClinicAdmin creates valid schedules, invalid submissions are rejected, an unstaffed-doctor submission is rejected, and an unrelated staff member is forbidden.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T009 [P] [US1] Integration test: ClinicAdmin creates a Fixed-Time schedule (201, persisted+listable) and a Queue/Token schedule (201, `slotIntervalMinutes` null), in `backend/src/test/java/com/cms/scheduling/integration/CreateScheduleClinicAdminTest.java`
- [X] T010 [P] [US1] Integration test: every validation rule (QUEUE+interval, FIXED_TIME with no/non-positive interval, start≥end, empty days, duplicate day de-duplicated not rejected) rejected/accepted per spec, in `backend/src/test/java/com/cms/scheduling/integration/CreateScheduleValidationTest.java`
- [X] T011 [P] [US1] Integration test: doctor with no/inactive Role Assignment at the clinic → `409 DOCTOR_NOT_STAFFED_AT_CLINIC`; unknown clinic/doctor id → `404`, in `backend/src/test/java/com/cms/scheduling/integration/CreateScheduleStaffingGateTest.java`
- [X] T012 [P] [US1] Integration test: a staff member who is neither this clinic's ClinicAdmin nor the named doctor → `403 FORBIDDEN` on both `POST` and `GET`; no token → `401`, in `backend/src/test/java/com/cms/scheduling/integration/CreateScheduleForbiddenTest.java`
- [X] T013 [P] [US1] Integration test: `GET` lists exactly the schedules created for that doctor/clinic pair, in `backend/src/test/java/com/cms/scheduling/integration/ListSchedulesTest.java`

### Implementation for User Story 1

- [X] T014 [US1] Implement `ScheduleService.create()`/`.list()` per data-model.md (auth → validation → staffing gate → save) in `backend/src/main/java/com/cms/scheduling/ScheduleService.java` (depends on T006)
- [X] T015 [US1] Implement `ScheduleController` (`POST`/`GET /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules`) in `backend/src/main/java/com/cms/scheduling/ScheduleController.java` (depends on T014, T007)
- [X] T016 [US1] Implement `ScheduleExceptionHandler` mapping T002's exceptions to contracts/schedule.md's error shapes (reusing `com.cms.identity.api.dto.ErrorResponse`) in `backend/src/main/java/com/cms/scheduling/ScheduleExceptionHandler.java` (depends on T002)
- [X] T017 [P] [US1] Create `ScheduleForm.tsx` — days/time-range/mode/slot-interval inputs, submits via the API client, shows the created schedule and any validation/forbidden error — in `frontend/src/features/scheduling/ScheduleForm.tsx`
- [X] T018 [P] [US1] Create `api.ts` fetch client — `createSchedule`/`listSchedules(clinicId, doctorProfileId, ..., token)`, bearer-token pattern per `staff-onboarding/api.ts` — in `frontend/src/features/scheduling/api.ts`
- [X] T019 [US1] Frontend test: submits a valid Fixed-Time schedule and shows success; shows the FORBIDDEN/INVALID_SCHEDULE error messages — in `frontend/tests/scheduling/ScheduleForm.test.tsx` (depends on T017, T018)

**Checkpoint**: User Story 1 fully functional and independently testable.

---

## Phase 4: User Story 2 - The Doctor Defines Their Own Schedule (Priority: P2)

**Goal**: A Doctor's own staff token can create/list a schedule for themselves, and is forbidden from naming another doctor.

**Independent Test**: Per quickstart.md Scenario 4 — Doctor self-service succeeds; naming a different doctor is forbidden.

### Tests for User Story 2 (write first, confirm they FAIL before this phase's own additions — the authorization branch already exists from US1, these tests validate the doctor-self branch specifically)

- [X] T020 [P] [US2] Integration test: a Doctor's own token creates/lists a schedule for themselves (201/200), in `backend/src/test/java/com/cms/scheduling/integration/CreateScheduleDoctorSelfTest.java`
- [X] T021 [P] [US2] Integration test: a Doctor's own token attempting to create a schedule naming a *different* doctor → `403 FORBIDDEN`, in `backend/src/test/java/com/cms/scheduling/integration/CreateScheduleDoctorSelfTest.java`

### Implementation for User Story 2

- [X] T022 [US2] Verify/finish the doctor-self authorization branch in `ScheduleService` against T020–T021 (the branch was already designed into T014's authorization step; extend only if a test fails) in `backend/src/main/java/com/cms/scheduling/ScheduleService.java`

**Checkpoint**: Both user stories independently functional — ClinicAdmin and Doctor self-service both work, each with correct forbidden-path rejection.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T023 Run `quickstart.md` Scenarios 1–7 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T024 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in prior features' tests

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational. No dependency on US2.
- **User Story 2 (Phase 4)**: Depends on US1's `ScheduleService`/`ScheduleController` existing (extends the same authorization branch and endpoints) — independently testable once built.
- **Polish (Phase 5)**: Depends on both stories.

### Parallel Opportunities

- T001, T002, T003 in parallel (different files).
- T009–T013 (all US1 backend tests) in parallel — depend only on T008.
- T017, T018 in parallel within US1 implementation.
- T020–T021 (US2 tests) in parallel — depend on US1's implementation (T014–T016).

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenarios 1, 2, 3, 5, 6, 7 pass.

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenario 4 passes.
3. Phase 5: full-suite verification and quickstart sign-off.
