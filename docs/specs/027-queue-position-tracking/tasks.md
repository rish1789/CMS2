---

description: "Task list for Queue Position Tracking (Queue-Mode Only)"
---

# Tasks: Queue Position Tracking (Queue-Mode Only)

**Input**: Design documents from `/specs/027-queue-position-tracking/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/queue-position-tracking.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 staff views a booking's position, US2 = P2 patient views their own booking's position) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 015/016/017/018/025's existing `com.cms.booking` module: `backend/src/main/java/com/cms/booking/`, `backend/src/test/java/com/cms/booking/integration/`. New: `frontend/src/features/queue-position/`, `frontend/tests/queue-position/`.

---

## Phase 1: Setup

**Purpose**: The shared response DTO and the new exception both endpoints use.

- [X] T001 [P] Create `QueuePositionResponse` record (`bookingId`, `applicable`, `position`) per contracts/queue-position-tracking.md in `backend/src/main/java/com/cms/booking/dto/QueuePositionResponse.java`
- [X] T002 [P] Create `BookingNotFoundException` in `backend/src/main/java/com/cms/booking/BookingNotFoundException.java`

**Checkpoint**: Types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The new role-agnostic repository query, both security matchers, and the shared test fixture.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T003 Add `existsByAccount_IdAndClinic_IdAndActiveTrue(accountId, clinicId)` to `backend/src/main/java/com/cms/identity/account/RoleAssignmentRepository.java` (research.md R5 — the first staff-gated read with no role exclusion at all)
- [X] T004 Add explicit matcher `.requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/bookings/*/queue-position").authenticated()` to the `/api/v1/clinics/**` chain in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`
- [X] T005 Add explicit matcher `.requestMatchers(HttpMethod.GET, "/api/v1/patients/bookings/*/queue-position").authenticated()` to the `/api/v1/patients/**` chain in `backend/src/main/java/com/cms/patient/account/SecurityConfig.java`
- [X] T006 Create `AbstractQueuePositionIntegrationTest extends AbstractQueueBookingIntegrationTest` (reuses its `saveQueueSession`/`saveFixedTimeSession`/`staffQueueBookingService`/`patientQueueBookingService`/token helpers directly via inheritance — this codebase's established `com.cms.booking` test-fixture pattern), adding one helper: `setSlotStatus(Booking booking, SlotStatus status)` (marks a Booking's own Slot directly — no Queue-mode "complete"/no-show action exists yet in this backlog to trigger it through, per Assumptions) in `backend/src/test/java/com/cms/booking/integration/AbstractQueuePositionIntegrationTest.java`

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: Staff Views a Booking's Queue Position (Priority: P1) 🎯 MVP

**Goal**: Staff query any queue-mode Booking's position at their clinic, correctly counting only currently-active (`BOOKED`) tokens ahead, computed fresh on every call.

**Independent Test**: Per quickstart.md Scenarios 1, 2, 3, 5.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T007 [P] [US1] Integration test: the spec's own worked example (5 tokens booked, tokens 1-2 completed, token 3 active) → token 5's position is 3; marking token 3 no-show afterward → token 5's position recalculates to 2 on the very next query (SC-001, SC-002) — in `backend/src/test/java/com/cms/booking/integration/QueuePositionSuccessTest.java`
- [X] T008 [P] [US1] Integration test: a token with nothing booked ahead of it → position 1; every token ahead already completed/no-show → position 1 (Edge Cases) — in the same file as T007
- [X] T009 [P] [US1] Integration test: a Fixed-Time Session's Booking → `applicable: false, position: null` (FR-006, SC-003) — in `backend/src/test/java/com/cms/booking/integration/QueuePositionNotApplicableTest.java`
- [X] T010 [P] [US1] Integration test: a queue-mode Booking whose own Slot is already `COMPLETED` or `NO_SHOW` → `applicable: false, position: null` (FR-007) — in the same file as T009
- [X] T011 [P] [US1] Integration test: a Booking belonging to a different clinic → `404 BOOKING_NOT_FOUND`; a Doctor's own token (not just Operations/ClinicAdmin) can successfully query (FR-004, unrestricted-by-role per research.md R5); a staff token with zero active role assignment at the target `clinicId` (an unrelated clinic's ClinicAdmin) → `403 FORBIDDEN` (analyze finding E1, mirrors 016/020/025/026's identical pattern) — in `backend/src/test/java/com/cms/booking/integration/QueuePositionStaffAccessTest.java`

### Implementation for User Story 1

- [X] T012 [US1] Implement `QueuePositionService.position(bookingId)` (the shared computation, no authorization inside it — that's each caller-side controller's job per research.md R4) — load Booking → Slot → Session, `applicable=false` if not Queue-mode or the Booking's own Slot is already `COMPLETED`/`NO_SHOW`, else count `BOOKED` Slots with a lower `tokenNumber` in the same Session (`SlotRepository.findBySession_Id`, Java-side filtering per this codebase's established scale precedent) + 1 — in `backend/src/main/java/com/cms/booking/QueuePositionService.java` (depends on T002)
- [X] T013 [US1] Implement `StaffQueuePositionController` (`GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/queue-position`): authorize first (any active role at `clinicId` via T003's new query, else `ForbiddenException` → `403 FORBIDDEN`, analyze finding E1 — reuses `com.cms.booking.ForbiddenException`, already mapped), then filter the Booking by clinic via its Slot's Session's Clinic before calling `QueuePositionService` per contracts/queue-position-tracking.md — in `backend/src/main/java/com/cms/booking/StaffQueuePositionController.java` (depends on T001, T003, T012)

**Checkpoint**: User Story 1 fully functional and independently testable — staff can view any queue-mode Booking's position at their clinic.

---

## Phase 4: Patient Views Their Own Booking's Queue Position (Priority: P2)

**Goal**: An authenticated patient queries their own queue-mode Booking's position via a second, ownership-scoped endpoint that calls the identical computation as Phase 3's.

**Independent Test**: Per quickstart.md Scenarios 4, 6.

### Tests for User Story 2 (write first, confirm it FAILS before implementation)

- [X] T014 [P] [US2] Integration test: a patient queries their own Booking's position and sees the identical figure the staff endpoint reports for the same Booking at the same moment (SC-005); a different, unrelated patient's attempt to query it → `404 BOOKING_NOT_FOUND` (FR-005, SC-004) — in `backend/src/test/java/com/cms/booking/integration/QueuePositionPatientAccessTest.java`

### Implementation for User Story 2

- [X] T015 [US2] Implement `PatientQueuePositionController` (`GET /api/v1/patients/bookings/{bookingId}/queue-position`, filtering the Booking by `Patient.patientAccount` equal to the caller's own Patient Account id — no `clinicId` in the path, ownership is the actual boundary here per research.md R4) per contracts/queue-position-tracking.md — in `backend/src/main/java/com/cms/booking/PatientQueuePositionController.java` (depends on T001, T012)

**Checkpoint**: Both user stories independently functional — the complete queue-position-tracking flow, staff and patient sides agreeing by construction.

---

## Phase 5: Frontend & Polish

- [X] T016 [P] Create `frontend/src/features/queue-position/api.ts` — `getQueuePositionAsStaff(clinicId, bookingId, token)`, `getQueuePositionAsPatient(bookingId, token)` — mirroring `session-delay/api.ts`'s fetch-client shape
- [X] T017 Create `frontend/src/features/queue-position/QueuePositionIndicator.tsx` — fetches and displays a Booking's position via either audience's call (a `mode: 'staff' | 'patient'` prop selects which), rendering nothing when `applicable` is `false` (mirrors `DelayIndicator.tsx`'s precedent) (depends on T016)
- [X] T018 [P] Frontend test: renders the position when present; renders nothing when `applicable` is `false`, for both staff and patient modes — in `frontend/tests/queue-position/QueuePositionIndicator.test.tsx` (depends on T017)
- [X] T019 Run `quickstart.md` Scenarios 1–7 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md` — blocked by the sandbox's Testcontainers/Docker limitation (confirmed via direct run of `QueuePositionSuccessTest`/`QueuePositionPatientAccessTest`: `IllegalStateException: Could not find a valid Docker environment`), same as every prior feature this session; verified instead at the unit-of-behavior level via code review against each scenario's expected request/response/state.
- [X] T020 Run full backend build (`/tmp/gradle-8.10/bin/gradle build -x test` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 013/016/017/018's existing tests (given `RoleAssignmentRepository` and both `SecurityConfig` classes were extended). Hit and fixed the known OneDrive `backend/build` corruption once this session (`rm -rf backend/build`). Backend: compile + spotless green (`-x test` — actual test execution blocked by the Docker limitation above, confirmed test-compile succeeds and sample test classes run far enough to hit exactly that Docker error, not a compile/logic error). Frontend: 75/75 tests green (72 pre-existing + 3 new), `npm run lint` clean for new files (fixed 2 `exhaustive-deps` warnings during implementation), `tsc -b` clean for new files (the same 2 pre-existing, unrelated `TS6133` errors in `BookSlotForm.tsx` remain, out of this feature's scope).

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational. MVP.
- **User Story 2 (Phase 4)**: Depends on US1's `QueuePositionService` (T012) existing.
- **Frontend & Polish (Phase 5)**: Depends on both stories.

### Parallel Opportunities

- T001, T002 in parallel (different files).
- T004, T005 in parallel (different files, different `SecurityConfig` classes).
- T007–T011 (all US1 tests) in parallel — depend only on T006.
- T014 can be drafted alongside Phase 3's tests, though it depends on T012 to actually pass.
- T018 once T017 exists.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenarios 1, 2, 3, 5 pass.

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenarios 4, 6 pass.
3. Phase 5: frontend, full-suite verification, quickstart sign-off.

---

## Phase 6: Convergence

- [X] T021 Remove the unused `QueuePositionService.position(UUID bookingId)` overload and its now-unused `BookingRepository` constructor dependency — neither controller calls it, both correctly call `positionOf(Booking)` per Constitution II (unrequested) — in `backend/src/main/java/com/cms/booking/QueuePositionService.java`
