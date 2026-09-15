---

description: "Task list for Patient Self-Service Fixed-Time Booking"
---

# Tasks: Patient Self-Service Fixed-Time Booking

**Input**: Design documents from `/specs/021-patient-self-service-booking/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/patient-booking.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 list+book as an already-linked patient, US2 = P2 first-time auto-link) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 015/016's existing modules: `backend/src/main/java/com/cms/booking/`, `backend/src/main/java/com/cms/scheduling/`, `backend/src/main/java/com/cms/patient/account/`, `backend/src/test/java/com/cms/booking/integration/`. New: `frontend/src/features/patient-booking/`, `frontend/tests/patient-booking/`.

---

## Phase 1: Setup

**Purpose**: New DTOs and the new repository query shared by both stories.

- [X] T001 [P] Create `PatientBookSlotRequest` and `OpenSlotResponse` DTOs per contracts/patient-booking.md in `backend/src/main/java/com/cms/booking/dto/`
- [X] T002 [P] Add `findOpenFixedTimeSlots(clinicId, doctorProfileId)` query to `backend/src/main/java/com/cms/scheduling/SlotRepository.java` per data-model.md

**Checkpoint**: Types and query exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The patient JWT authentication filter, its wiring, and the shared test fixture both stories build on.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T003 [P] Create `PatientAuthenticationEntryPoint` (mirrors `StaffAuthenticationEntryPoint`) in `backend/src/main/java/com/cms/patient/account/PatientAuthenticationEntryPoint.java`
- [X] T004 [P] Create `PatientJwtAuthenticationFilter` (mirrors `StaffJwtAuthenticationFilter`; NOT a `@Component`, per research.md) in `backend/src/main/java/com/cms/patient/account/PatientJwtAuthenticationFilter.java`
- [X] T005 Wire the filter into `com.cms.patient.account.SecurityConfig`'s existing `@Order(2)` chain via `addFilterBefore`, add its `exceptionHandling` entry point, and add explicit `authenticated()` matchers for `GET /api/v1/patients/clinics/*/slots` and `POST /api/v1/patients/clinics/*/slots/*/book` (research.md — closes the same silent-fallthrough gap class already fixed twice this session) in `backend/src/main/java/com/cms/patient/account/SecurityConfig.java` (depends on T003, T004)
- [X] T006 Create `AbstractPatientBookingIntegrationTest.java` — Testcontainers + MockMvc base class reusing 012/015/016's Slot/fee-configuration fixtures plus a Patient Account signup+login token helper — in `backend/src/test/java/com/cms/booking/integration/AbstractPatientBookingIntegrationTest.java` (depends on T005)

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: List and Book an Open Slot as an Already-Linked Patient (Priority: P1) 🎯 MVP

**Goal**: An authenticated patient can list a clinic's open Fixed-Time Slots and book one under their existing linked Patient record, with fee resolution/locking and race-safety identical to 016's.

**Independent Test**: Per quickstart.md Scenarios 1, 2, 4, 5, 6.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T007 [P] [US1] Integration test: listing returns only OPEN Fixed-Time Slots for the clinic (excludes BOOKED, excludes Queue-mode Sessions, supports the `doctorId` filter) in `backend/src/test/java/com/cms/booking/integration/PatientOpenSlotListingTest.java`
- [X] T008 [P] [US1] Integration test: booking as an already-linked patient succeeds (201), Slot becomes BOOKED and disappears from a subsequent list call, `lockedFee` matches resolution, `paymentStatus = PENDING` in `backend/src/test/java/com/cms/booking/integration/PatientBookingExistingLinkTest.java`
- [X] T009 [P] [US1] Integration test: no fee resolvable → `409 NO_FEE_CONFIGURED`, zero Booking rows created, in `backend/src/test/java/com/cms/booking/integration/PatientBookingFeeBlockTest.java`
- [X] T010 [P] [US1] Integration test: booking an already-BOOKED Slot → `409 SLOT_ALREADY_BOOKED`; two concurrent booking attempts against one fresh Slot → exactly one succeeds; a nonexistent Slot ID (or one belonging to a different clinic) → `404 SLOT_NOT_FOUND` (FR-009); an appointment type that doesn't exist or doesn't belong to the Slot's doctor → `404 APPOINTMENT_TYPE_NOT_FOUND` (FR-010) — five `@Test` methods total, in `backend/src/test/java/com/cms/booking/integration/PatientBookingAlreadyBookedTest.java`
- [X] T011 [P] [US1] Integration test: no token / expired / malformed token → `401 UNAUTHORIZED` on both the list and book endpoints, in `backend/src/test/java/com/cms/booking/integration/PatientBookingAuthenticationTest.java`

### Implementation for User Story 1

- [X] T012 [US1] Implement `PatientBookingService` — `listOpenSlots(clinicId, doctorProfileId)` and `bookSlot(patientAccountId, clinicId, slotId, PatientBookSlotInput)`, reusing `FeeResolutionService`, `AppointmentTypeRepository`, `BookingRepository`, and `PatientLinkingService.findOrCreatePatient` exactly per research.md/data-model.md, in `backend/src/main/java/com/cms/booking/PatientBookingService.java` (depends on T001, T002, T006)
- [X] T013 [US1] Implement `PatientBookingController` (`GET /api/v1/patients/clinics/{clinicId}/slots`, `POST /api/v1/patients/clinics/{clinicId}/slots/{slotId}/book`) per contracts/patient-booking.md in `backend/src/main/java/com/cms/booking/PatientBookingController.java` (depends on T012)

**Checkpoint**: User Story 1 fully functional and independently testable.

---

## Phase 4: First-Ever Booking at a Clinic Auto-Creates the Patient Record (Priority: P2)

**Goal**: Confirm a patient's first booking at a clinic transparently creates or phone-matches a Patient record, with no separate step.

**Independent Test**: Per quickstart.md Scenario 3.

**Implementation note**: No new implementation task — T012 already calls the existing, already-converged `PatientLinkingService.findOrCreatePatient` uniformly for every booking, which itself implements the create-new and phone-match-and-link behaviors (019, FR-003/FR-004). This phase is test-only, confirming that already-delivered behavior end-to-end through this feature's own endpoint.

### Tests for User Story 2 (write first, confirm they FAIL before US1's T012 is implemented — same file may be written alongside Phase 3's tests)

- [X] T014 [P] [US2] Integration test: a Patient Account with no existing Patient record at a clinic and no phone match creates a new Patient record and links it on first booking; a Patient Account whose phone matches an existing unlinked walk-in Patient record at that clinic gets that record linked (not duplicated) instead — two `@Test` methods in `backend/src/test/java/com/cms/booking/integration/PatientBookingFirstTimeLinkTest.java`

**Checkpoint**: Both user stories independently functional — the complete patient self-service booking flow.

---

## Phase 5: Frontend & Polish

- [X] T015 [P] Create `frontend/src/features/patient-account/token.ts` — sessionStorage-backed Patient Account session (mirrors `staff-login/token.ts`) — and wire `LoginForm.tsx` to persist the session on successful login, since no patient-authenticated frontend feature has needed this before now, in `frontend/src/features/patient-account/token.ts` and `frontend/src/features/patient-account/LoginForm.tsx`
- [X] T016 [P] Create `api.ts` fetch client (`listOpenSlots`, `bookSlot`) in `frontend/src/features/patient-booking/api.ts` (bearer-token pattern per `staff-booking`'s existing client)
- [X] T017 Create `OpenSlotList.tsx` — fetches and renders open Slots for a clinic (optional doctor filter), each with a "Book" action — in `frontend/src/features/patient-booking/OpenSlotList.tsx` (depends on T015, T016)
- [X] T018 Create `BookSlotForm.tsx` — patient name + appointment-type selection for a chosen Slot, shows the locked fee or error on success/failure — in `frontend/src/features/patient-booking/BookSlotForm.tsx` (depends on T015, T016)
- [X] T019 [P] Frontend test: lists open Slots and supports the doctor filter; shows an empty state — in `frontend/tests/patient-booking/OpenSlotList.test.tsx` (depends on T017)
- [X] T020 [P] Frontend test: submits a valid booking and shows the locked fee; shows the `SLOT_ALREADY_BOOKED`/`NO_FEE_CONFIGURED` error messages — in `frontend/tests/patient-booking/BookSlotForm.test.tsx` (depends on T018)
- [X] T021 Run `quickstart.md` Scenarios 1–6 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T022 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 012/015/016/019/039's existing tests (given `SlotRepository` and `com.cms.patient.account.SecurityConfig` were extended)

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational.
- **User Story 2 (Phase 4)**: Depends on US1's T012 existing (its test exercises the same implementation).
- **Frontend & Polish (Phase 5)**: Depends on both stories.

### Parallel Opportunities

- T001, T002 in parallel (different files).
- T003, T004 in parallel (different files).
- T007–T011 (all US1 tests) in parallel — depend only on T006.
- T014 can be drafted alongside Phase 3's tests, though it exercises the same T012 implementation.
- T015, T016 in parallel (different files); T019, T020 in parallel once their respective components exist.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenarios 1, 2, 4, 5, 6 pass.

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenario 3 passes.
3. Phase 5: frontend, full-suite verification, quickstart sign-off.

---

## Phase 6: Convergence

- [X] T023 Fix `PatientLinkingService.findOrCreatePatient()`'s create-new branch: change `patientRepository.save(new Patient(...))` to `patientRepository.saveAndFlush(new Patient(...))` so the `uq_patient_clinic_account` unique-constraint check happens synchronously at that line (inside the existing try/catch), not deferred to a later, unrelated flush (021's own `bookingRepository.saveAndFlush(booking)`) where the catch here can never intercept it and the exception gets mis-translated into `SlotAlreadyBookedException` by the caller — in `backend/src/main/java/com/cms/patient/record/PatientLinkingService.java` per Constitution IV (contradicts)
- [X] T024 Integration test: two concurrent first-time bookings for the *same* Patient Account at the *same* clinic (against two different Slots) result in exactly one `Patient` record, and both bookings succeed under it — in `backend/src/test/java/com/cms/booking/integration/PatientBookingFirstTimeLinkTest.java`
