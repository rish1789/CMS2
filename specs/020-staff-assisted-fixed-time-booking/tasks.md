---

description: "Task list for Staff-Assisted Fixed-Time Booking"
---

# Tasks: Staff-Assisted Fixed-Time Booking

**Input**: Design documents from `/specs/020-staff-assisted-fixed-time-booking/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/staff-booking.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 existing-patient booking core, US2 = P2 walk-in patient creation) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 012/015's existing modules: `backend/src/main/java/com/cms/scheduling/`, `backend/src/main/java/com/cms/booking/`, `backend/src/test/java/com/cms/booking/integration/`. New: `frontend/src/features/staff-booking/`, `frontend/tests/staff-booking/`.

---

## Phase 1: Setup

**Purpose**: New entities/enums/DTOs/exceptions/migration shared by both stories.

- [X] T001 [P] Add `BOOKED` to `SlotStatus` and a `setStatus(SlotStatus)` mutator to `Slot` in `backend/src/main/java/com/cms/scheduling/SlotStatus.java` and `Slot.java`
- [X] T002 [P] Create `PaymentStatus` enum (`PENDING`, `PAID`) in `backend/src/main/java/com/cms/booking/PaymentStatus.java`
- [X] T003 [P] Create exceptions `SlotNotFoundException`, `SlotAlreadyBookedException`, `PatientNotFoundException`, `InvalidMobileNumberException` in `backend/src/main/java/com/cms/booking/`
- [X] T004 [P] Create `BookSlotRequest`, `BookingResponse` DTOs per contracts/staff-booking.md in `backend/src/main/java/com/cms/booking/dto/`
- [X] T005 Create migration `V12__create_booking.sql` — `booking` table with unique `slot_id` per data-model.md — in `backend/src/main/resources/db/migration/V12__create_booking.sql`
- [X] T006 Create `Booking` entity per data-model.md in `backend/src/main/java/com/cms/booking/Booking.java` (depends on T002, T005)

**Checkpoint**: Schema and types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Repository, the new security matcher, and shared test fixture both stories build on.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T007 Create `BookingRepository` in `backend/src/main/java/com/cms/booking/BookingRepository.java` (depends on T006)
- [X] T008 Add explicit `POST /api/v1/clinics/*/slots/*/book` matcher to `com.cms.identity.account.SecurityConfig`'s existing `@Order(1)` chain in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java` (research.md — closes the same silent-fallthrough gap class already fixed once this session)
- [X] T009 Create `AbstractStaffBookingIntegrationTest.java` — Testcontainers + MockMvc base class extending/reusing 012's Slot-generation fixture and 015's fee-configuration fixture, plus Operations/ClinicAdmin token helpers and Booking/Patient cleanup — in `backend/src/test/java/com/cms/booking/integration/AbstractStaffBookingIntegrationTest.java` (depends on T007, T008)

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: Staff Book an Existing Patient Into an Open Slot (Priority: P1) 🎯 MVP

**Goal**: `StaffBookingService.bookSlot` correctly resolves/locks the fee, creates the Booking, flips the Slot, rejects an already-booked Slot (incl. concurrently), and enforces authorization.

**Independent Test**: Per quickstart.md Scenarios 1, 3, 4, 5.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T010 [P] [US1] Integration test: booking an existing Patient into an OPEN Slot succeeds (201), Slot becomes BOOKED, `lockedFee` matches resolution, `paymentStatus = PENDING`, in `backend/src/test/java/com/cms/booking/integration/StaffBookingExistingPatientTest.java`
- [X] T011 [P] [US1] Integration test: no fee resolvable → `409 NO_FEE_CONFIGURED`, zero rows created of any kind, in `backend/src/test/java/com/cms/booking/integration/StaffBookingFeeBlockTest.java`
- [X] T012 [P] [US1] Integration test: booking an already-BOOKED Slot → `409 SLOT_ALREADY_BOOKED`; two concurrent booking attempts against one fresh Slot → exactly one succeeds, in `backend/src/test/java/com/cms/booking/integration/StaffBookingAlreadyBookedTest.java`
- [X] T013 [P] [US1] Integration test: Operations and ClinicAdmin tokens both succeed; a Doctor's own token (or an unrelated staff member's) is `403 FORBIDDEN`; no token is `401`, in `backend/src/test/java/com/cms/booking/integration/StaffBookingAuthorizationTest.java`

### Implementation for User Story 1

- [X] T014 [US1] Implement `StaffBookingService.bookSlot` (existing-patient path only for this task — walk-in creation added in US2) per data-model.md in `backend/src/main/java/com/cms/booking/StaffBookingService.java` (depends on T007, T009)
- [X] T015 [US1] Implement `StaffBookingController` (`POST /api/v1/clinics/{clinicId}/slots/{slotId}/book`) per contracts/staff-booking.md in `backend/src/main/java/com/cms/booking/StaffBookingController.java` (depends on T014)
- [X] T016 [US1] Extend `com.cms.booking.BookingExceptionHandler` (015) with mappings for T003's new exceptions, AND for 015's pre-existing `AppointmentTypeNotFoundException` (→ `404 APPOINTMENT_TYPE_NOT_FOUND`) and `NoFeeConfiguredException` (→ `409 NO_FEE_CONFIGURED`) — this feature is the first HTTP-reachable caller of `FeeResolutionService`, so neither was mapped before now — per contracts/staff-booking.md in `backend/src/main/java/com/cms/booking/BookingExceptionHandler.java` (depends on T003)

**Checkpoint**: User Story 1 fully functional and independently testable.

---

## Phase 4: Staff Create a Walk-In Patient as Part of Booking (Priority: P2)

**Goal**: `bookSlot` supports the new-patient path (name + optional validated phone), creating no Patient/Booking on validation failure.

**Independent Test**: Per quickstart.md Scenario 2.

### Tests for User Story 2 (write first, confirm they FAIL before implementation)

- [X] T017 [P] [US2] Integration test: booking with new-patient name+valid phone creates an unlinked Patient and proceeds to a successful booking; with no phone at all also succeeds, in `backend/src/test/java/com/cms/booking/integration/StaffBookingWalkInPatientTest.java`
- [X] T018 [P] [US2] Integration test (same file): an invalid phone → `400 INVALID_MOBILE_NUMBER`, zero Patient or Booking rows created, in `backend/src/test/java/com/cms/booking/integration/StaffBookingWalkInPatientTest.java`

### Implementation for User Story 2

- [X] T019 [US2] Add the new-walk-in-patient path to `StaffBookingService.bookSlot` per data-model.md (depends on T014)

**Checkpoint**: Both user stories independently functional — the complete staff-assisted booking flow.

---

## Phase 5: Frontend & Polish

- [X] T020 [P] Create `BookSlotForm.tsx` — existing-patient-id or new-patient-name/phone inputs, appointment-type id, submit, shows the locked fee or error — in `frontend/src/features/staff-booking/BookSlotForm.tsx`
- [X] T021 [P] Create `api.ts` fetch client (bearer-token pattern per `staff-onboarding`/`scheduling`'s existing clients) in `frontend/src/features/staff-booking/api.ts`
- [X] T022 Frontend test: submits a valid booking and shows the locked fee; shows the `SLOT_ALREADY_BOOKED`/`NO_FEE_CONFIGURED`/`INVALID_MOBILE_NUMBER` error messages — in `frontend/tests/staff-booking/BookSlotForm.test.tsx` (depends on T020, T021)
- [X] T023 Run `quickstart.md` Scenarios 1–6 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T024 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 012/015's existing tests (given `Slot` was extended)

---

## Phase 6: Convergence

- [X] T025 Fix `StaffBookingService.bookSlot()`: change `bookingRepository.save(booking)` to `bookingRepository.saveAndFlush(booking)` so the unique-constraint check on `slot_id` happens synchronously at that line (inside the existing try/catch), not deferred to the transaction's final commit where the catch can never intercept it — in `backend/src/main/java/com/cms/booking/StaffBookingService.java` per FR-006/SC-003, data-model.md (contradicts)

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational.
- **User Story 2 (Phase 4)**: Depends on US1's `bookSlot` existing (extends the same method).
- **Frontend & Polish (Phase 5)**: Depends on both stories.

### Parallel Opportunities

- T001–T004 in parallel (different files).
- T010–T013 (all US1 tests) in parallel — depend only on T009.
- T017, T018 (same file, independent `@Test` methods) can be drafted together.
- T020, T021 in parallel (different files).

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenarios 1, 3, 4, 5 pass.

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenario 2 passes.
3. Phase 5: frontend, full-suite verification, quickstart sign-off.
