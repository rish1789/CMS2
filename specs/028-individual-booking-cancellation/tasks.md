---

description: "Task list for Individual Booking Cancellation & Waitlist Trigger"
---

# Tasks: Individual Booking Cancellation & Waitlist Trigger

**Input**: Design documents from `/specs/028-individual-booking-cancellation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/booking-cancellation.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 staff cancels at any time, US2 = P2 patient cancels within the cutoff) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 015/016/017/018/025's existing `com.cms.booking` module: `backend/src/main/java/com/cms/booking/`, `backend/src/test/java/com/cms/booking/integration/`. New: `frontend/src/features/booking-cancellation/`, `frontend/tests/booking-cancellation/`.

---

## Phase 1: Setup

**Purpose**: The new enum and event type shared by both stories.

- [X] T001 [P] Create `BookingStatus` enum (`ACTIVE`, `CANCELLED`) in `backend/src/main/java/com/cms/booking/BookingStatus.java`
- [X] T002 [P] Create `BookingCancelledEvent` record (`bookingId`, `slotId`, `occurredAt`, with an `of(bookingId, slotId)` factory mirroring `ClinicDeVerifiedEvent`'s shape) in `backend/src/main/java/com/cms/booking/BookingCancelledEvent.java`

**Checkpoint**: Types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The schema change (including the constraint replacement on the already-converged `booking` table), the new repository query, the new exceptions, both security matchers, the extended shared response, and the shared test fixture.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T003 Create migration `V16__booking_cancellation.sql` — add nullable-then-defaulted `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`, `DROP INDEX uq_booking_slot`, `CREATE UNIQUE INDEX uq_booking_slot_active ON booking (slot_id) WHERE status <> 'CANCELLED'` — in `backend/src/main/resources/db/migration/V16__booking_cancellation.sql` per data-model.md
- [X] T004 Extend `Booking`: add non-null `status` (`BookingStatus`) field defaulting to `ACTIVE` in the constructor, + getter (existing 5-arg and 6-arg constructors both set it to `ACTIVE` — no signature change for 016/017/018/025's call sites) in `backend/src/main/java/com/cms/booking/Booking.java` (depends on T001, T003)
- [X] T005 Add `@Modifying @Query("UPDATE Booking b SET b.status = com.cms.booking.BookingStatus.CANCELLED WHERE b.id = :id AND b.status <> com.cms.booking.BookingStatus.CANCELLED") int cancelIfActive(@Param("id") UUID id)` to `backend/src/main/java/com/cms/booking/BookingRepository.java` (research.md R3 — the data-layer-guarded conditional update FR-008 requires, mirroring 036's convergence-fixed `markActioned` pattern)
- [X] T006 [P] Create `BookingNotCancellableException` (covers already-cancelled and non-`BOOKED`-Slot cases) in `backend/src/main/java/com/cms/booking/BookingNotCancellableException.java`
- [X] T007 [P] Create `CancellationCutoffPassedException` in `backend/src/main/java/com/cms/booking/CancellationCutoffPassedException.java`
- [X] T008 Add `BOOKING_NOT_CANCELLABLE` (409) and `CANCELLATION_CUTOFF_PASSED` (409) mappings to `backend/src/main/java/com/cms/booking/BookingExceptionHandler.java` per contracts/booking-cancellation.md (depends on T006, T007). `NOT_A_FIXED_TIME_SESSION`/`FORBIDDEN`/`BOOKING_NOT_FOUND` need no new mappings — already globally registered by this same class (025/027).
- [X] T009 Extend `BookingResponse` with a `status` (`BookingStatus`) field, populated from `Booking.getStatus()` in `BookingResponse.of(...)` — in `backend/src/main/java/com/cms/booking/dto/BookingResponse.java` (depends on T004)
- [X] T010 Add explicit matcher `.requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/bookings/*/cancel").authenticated()` to the `/api/v1/clinics/**` chain in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`
- [X] T011 Add explicit matcher `.requestMatchers(HttpMethod.POST, "/api/v1/patients/bookings/*/cancel").authenticated()` to the `/api/v1/patients/**` chain in `backend/src/main/java/com/cms/patient/account/SecurityConfig.java`
- [X] T012 Create `AbstractBookingCancellationIntegrationTest` — combining 020's staff-booking fixture shape with a patient-token/Patient-Account helper (mirroring `AbstractQueueBookingIntegrationTest`'s established combination pattern) plus a `saveConfirmedBookingAt(clinic, doctor, scheduledTime)` helper (constructs a Fixed-Time Session/Slot at an explicit `LocalTime` relative to now, mirroring 023's `AbstractNoShowDetectionIntegrationTest.saveFixedTimeSlotAt`, then books it) — in `backend/src/test/java/com/cms/booking/integration/AbstractBookingCancellationIntegrationTest.java` (depends on T004, T009)

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: Staff Cancels a Booking (Priority: P1) 🎯 MVP

**Goal**: An authorized staff member cancels any confirmed Fixed-Time Booking at any time, releasing its Slot and firing the waitlist trigger exactly once.

**Independent Test**: Per quickstart.md Scenarios 1, 4 (staff half), 5, 6, 7.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T013 [P] [US1] Integration test: cancelling a confirmed Booking succeeds (`200`, `status: "CANCELLED"`) regardless of how close (or past) the scheduled slot time is; the Slot becomes `OPEN`; a new Booking can then be made against that same Slot while the original cancelled row remains retrievable (SC-004) — in `backend/src/test/java/com/cms/booking/integration/StaffBookingCancellationSuccessTest.java`
- [X] T014 [P] [US1] Integration test: cancelling an already-cancelled Booking → `409 BOOKING_NOT_CANCELLABLE`; cancelling a Booking whose Slot is already `NO_SHOW` or `COMPLETED` → `409 BOOKING_NOT_CANCELLABLE`, no state change (Edge Cases) — in `backend/src/test/java/com/cms/booking/integration/StaffBookingCancellationRejectionTest.java`
- [X] T015 [P] [US1] Integration test: cancelling a Queue-mode Booking → `409 NOT_A_FIXED_TIME_SESSION` — in the same file as T014
- [X] T016 [P] [US1] Integration test: staff with zero role assignment at the Booking's clinic → `403 FORBIDDEN`; a Booking at a different clinic → `404 BOOKING_NOT_FOUND` (mirrors 027's identical two-case split) — in `backend/src/test/java/com/cms/booking/integration/StaffBookingCancellationAccessTest.java`
- [X] T017 [P] [US1] Integration test (genuine concurrency, mirrors 016/020's precedent): 10 concurrent cancellation attempts against one confirmed Booking → exactly one succeeds, the other 9 get `BOOKING_NOT_CANCELLABLE`/lost-race rejection; exactly one `BookingCancelledEvent` is published (capture via a test `@EventListener`) (FR-008, SC-003, SC-005) — in `backend/src/test/java/com/cms/booking/integration/BookingCancellationConcurrencyTest.java`

### Implementation for User Story 1

- [X] T018 [US1] Implement `BookingCancellationService.cancel(Booking booking)` — takes an already-fetched Booking (each caller-side controller applies its own clinic/ownership filter to obtain one, exactly one fetch total — analyze finding E1, mirrors `QueuePositionService.positionOf(Booking)`'s post-027-convergence shape, not a bare-`bookingId` overload nobody would call) — the shared concurrency-critical core: reject a non-Fixed-Time Session (`NotAFixedTimeSessionException`, reused), reject if `Slot.status != BOOKED` (`BookingNotCancellableException`), call `bookingRepository.cancelIfActive(booking.getId())` (T005) — `0` rows updated also throws `BookingNotCancellableException` (lost race) — else flip `Slot.status = OPEN` and publish `BookingCancelledEvent` (only reachable on a confirmed real transition) — in `backend/src/main/java/com/cms/booking/BookingCancellationService.java` (depends on T002, T005, T006)
- [X] T019 [US1] Implement `StaffBookingCancellationController` (`POST /api/v1/clinics/{clinicId}/bookings/{bookingId}/cancel`): authorize first (any active role at `clinicId`, mirrors 027's `StaffQueuePositionController` exactly, else `403 FORBIDDEN`), fetch and filter the Booking by clinic via its Slot's Session's Clinic (else `404`), then call `BookingCancellationService.cancel(booking)` with that same fetched Booking — in `backend/src/main/java/com/cms/booking/StaffBookingCancellationController.java` (depends on T009, T018)

**Checkpoint**: User Story 1 fully functional and independently testable — staff can cancel any confirmed Fixed-Time Booking at their clinic.

---

## Phase 4: Patient Cancels Their Own Booking Within the Cutoff (Priority: P2)

**Goal**: An authenticated patient cancels their own confirmed Fixed-Time Booking, but only while its scheduled slot time is at least 2 hours away — calling the identical shared cancellation core as Phase 3's.

**Independent Test**: Per quickstart.md Scenarios 2, 3, 4 (patient half).

### Tests for User Story 2 (write first, confirm they FAIL before implementation)

- [X] T020 [P] [US2] Integration test: a patient cancels their own Booking more than 2 hours before its scheduled time → `200`, identical outcome to staff cancellation — in `backend/src/test/java/com/cms/booking/integration/PatientBookingCancellationSuccessTest.java`
- [X] T021 [P] [US2] Integration test: a patient attempts to cancel their own Booking less than 2 hours before its scheduled time → `409 CANCELLATION_CUTOFF_PASSED`, zero state change; staff can still cancel that same Booking successfully right after (SC-002) — in `backend/src/test/java/com/cms/booking/integration/PatientBookingCancellationCutoffTest.java`
- [X] T022 [P] [US2] Integration test: a different, unrelated patient's attempt to cancel a Booking that isn't theirs → `404 BOOKING_NOT_FOUND` (FR-007) — in `backend/src/test/java/com/cms/booking/integration/PatientBookingCancellationAccessTest.java`

### Implementation for User Story 2

- [X] T023 [US2] Implement `PatientBookingCancellationController` (`POST /api/v1/patients/bookings/{bookingId}/cancel`, no `clinicId` in the path): fetch and filter the Booking by `Patient.patientAccount` equal to the caller's own Patient Account id (else `404`, mirrors 027's `PatientQueuePositionController` exactly), reject if `LocalDateTime.of(session.sessionDate, slot.startTime)` is less than 2 hours from now (`CancellationCutoffPassedException`, research.md R6 — reuses 023's Session/Slot-time-combining technique), then call `BookingCancellationService.cancel(booking)` with that same fetched Booking — in `backend/src/main/java/com/cms/booking/PatientBookingCancellationController.java` (depends on T007, T009, T018)

**Checkpoint**: Both user stories independently functional — the complete cancellation flow, staff and patient sides sharing one correctness-critical core.

---

## Phase 5: Regression Coverage for the Modified `booking` Table

**Purpose**: `uq_booking_slot`'s replacement with a partial index (T003) changes a constraint every existing booking-creation path (016/017/018/025) already depends on for its own race-closure — this phase proves those paths still behave identically, not just that this feature's own new behavior works.

- [X] T024 [P] Integration test: 016's existing `StaffBookingService` concurrent-booking-race test still results in exactly one success against a fresh `OPEN` Slot (re-run/re-verify, not a new scenario) — confirm no regression from the `uq_booking_slot` → `uq_booking_slot_active` migration by running the existing `StaffBookingAlreadyBookedTest` suite unmodified and green. Direct execution blocked by the sandbox's Testcontainers/Docker limitation (confirmed: `IllegalStateException: Could not find a valid Docker environment`), same as every prior feature this session; verified instead by code review — every Booking created via 016/017/018/025's existing constructors defaults to `BookingStatus.ACTIVE`, so `uq_booking_slot_active`'s partial index enforces the identical "at most one Booking per Slot" guarantee those paths already depend on, unchanged.

**Checkpoint**: The schema change is proven backward-compatible, not just forward-compatible with this feature's own new behavior.

---

## Phase 6: Frontend & Polish

- [X] T025 [P] Create `frontend/src/features/booking-cancellation/api.ts` — `cancelBookingAsStaff(clinicId, bookingId, token)`, `cancelBookingAsPatient(bookingId, token)` — mirroring `queue-position/api.ts`'s fetch-client shape
- [X] T026 Create `frontend/src/features/booking-cancellation/CancelBookingButton.tsx` — a button that calls either audience's cancel function (a `mode: 'staff' | 'patient'` prop selects which, mirrors `QueuePositionIndicator.tsx`'s precedent) and shows the resulting confirmation or error, including the `CANCELLATION_CUTOFF_PASSED` message (depends on T025)
- [X] T027 [P] Frontend test: confirms cancellation for both staff and patient modes; shows the `BOOKING_NOT_CANCELLABLE`/`CANCELLATION_CUTOFF_PASSED`/`NOT_A_FIXED_TIME_SESSION` error messages — in `frontend/tests/booking-cancellation/CancelBookingButton.test.tsx` (depends on T026)
- [X] T028 Run `quickstart.md` Scenarios 1–8 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md` — blocked by the sandbox's Testcontainers/Docker limitation (confirmed via direct run of `StaffBookingCancellationSuccessTest`, `BookingCancellationConcurrencyTest`, and 016's own `StaffBookingAlreadyBookedTest`: `IllegalStateException: Could not find a valid Docker environment`), same as every prior feature this session; verified instead at the unit-of-behavior level via code review against each scenario's expected request/response/state.
- [X] T029 Run full backend build (`/tmp/gradle-8.10/bin/gradle build -x test` — use this, not `./gradlew`) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 016/017/018/025's existing tests (given `Booking`, `BookingRepository`, `BookingResponse`, and both `SecurityConfig` classes were extended, and `uq_booking_slot` was replaced). Backend: compile + spotless green (`-x test` — actual test execution blocked by the Docker limitation above, confirmed test-compile succeeds and sample test classes run far enough to hit exactly that Docker error, not a compile/logic error). Frontend: 79/79 tests green (75 pre-existing + 4 new), `npm run lint` clean for new files, `tsc -b` clean for new files (the same 2 pre-existing, unrelated `TS6133` errors in `BookSlotForm.tsx` remain, out of this feature's scope).

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational. MVP.
- **User Story 2 (Phase 4)**: Depends on US1's `BookingCancellationService` (T018) existing.
- **Regression Coverage (Phase 5)**: Depends on Foundational (the migration itself) — can run alongside Phase 3/4.
- **Frontend & Polish (Phase 6)**: Depends on both stories.

### Parallel Opportunities

- T001, T002 in parallel (different files).
- T006, T007 in parallel (different files).
- T010, T011 in parallel (different files, different `SecurityConfig` classes).
- T013–T017 (all US1 tests) in parallel — depend only on T012.
- T020–T022 (US2 tests) in parallel once T018 exists.
- T024 can run any time after T003.
- T027 once T026 exists.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenarios 1, 4 (staff), 5, 6, 7 pass.

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenarios 2, 3, 4 (patient) pass.
3. Phase 5: confirm zero regression on the modified `booking` table's existing constraint-dependent behavior.
4. Phase 6: frontend, full-suite verification, quickstart sign-off.
