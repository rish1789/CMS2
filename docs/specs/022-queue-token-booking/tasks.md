---

description: "Task list for Queue/Token Booking"
---

# Tasks: Queue/Token Booking

**Input**: Design documents from `/specs/022-queue-token-booking/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/queue-booking.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 staff-assisted, US2 = P2 patient self-service) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 013/015/016/019/021's existing modules: `backend/src/main/java/com/cms/booking/`, `backend/src/test/java/com/cms/booking/integration/`. New: `frontend/src/features/staff-booking/queueApi.ts`+`QueueBookSlotForm.tsx`, `frontend/src/features/patient-booking/queueApi.ts`+`QueueBookSlotForm.tsx`.

---

## Phase 1: Setup

- [X] T001 [P] Create `QueueBookSlotRequest`, `PatientQueueBookSlotRequest`, `QueueBookingResponse` DTOs per contracts/queue-booking.md and data-model.md in `backend/src/main/java/com/cms/booking/dto/`

**Checkpoint**: Types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T002 Extend `BookingExceptionHandler` with `SessionNotFoundException` → `404 SESSION_NOT_FOUND`, `NotAQueueSessionException` → `409 NOT_A_QUEUE_SESSION`, `TokenIssuanceFailedException` → `503 TOKEN_ISSUANCE_FAILED` per research.md/contracts/queue-booking.md in `backend/src/main/java/com/cms/booking/BookingExceptionHandler.java`
- [X] T003 Add explicit `authenticated()` matchers for `POST /api/v1/clinics/*/sessions/*/queue-bookings` (staff, `com.cms.identity.account.SecurityConfig`) and `POST /api/v1/patients/clinics/*/sessions/*/queue-bookings` (patient, `com.cms.patient.account.SecurityConfig`) — closes the same silent-fallthrough gap class already fixed in 014/020/021
- [X] T004 Create `AbstractQueueBookingIntegrationTest.java` — Testcontainers + MockMvc base class combining 013/015's Queue-mode Session fixture, 016's staff-token helpers, and 021's Patient Account/token helpers — in `backend/src/test/java/com/cms/booking/integration/AbstractQueueBookingIntegrationTest.java` (depends on T002, T003)

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: Staff Books a Patient into a Queue Session (Priority: P1) 🎯 MVP

**Goal**: `StaffQueueBookingService.bookSlot` mints a token via `QueueSlotService.issueNextSlot` (called from a non-transactional context per research.md), resolves/locks the fee, resolves/creates the Patient, and creates a Booking — with authorization and rejection paths matching 016's own.

**Independent Test**: Per quickstart.md Scenarios 1, 2, 4, 5, 7 (staff half).

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T005 [P] [US1] Integration test: booking an existing Patient into an active Queue Session succeeds (201), response includes a `tokenNumber`, `lockedFee` matches resolution, `paymentStatus = PENDING`, in `backend/src/test/java/com/cms/booking/integration/StaffQueueBookingTest.java`
- [X] T006 [P] [US1] Integration test (same file): a walk-in with no existing Patient record creates one as part of the booking
- [X] T007 [P] [US1] Integration test (same file): no fee resolvable → `409 NO_FEE_CONFIGURED`, zero Slot/Patient/Booking rows created
- [X] T008 [P] [US1] Integration test (same file): Operations and ClinicAdmin both succeed; a Doctor's own token or an unrelated staff member's is `403`; no token is `401`
- [X] T009 [P] [US1] Integration test (same file): a nonexistent Session ID (or one belonging to a different clinic) → `404 SESSION_NOT_FOUND`; a `FIXED_TIME`-mode Session → `409 NOT_A_QUEUE_SESSION`; an appointment type that doesn't exist or doesn't belong to the Session's doctor → `404 APPOINTMENT_TYPE_NOT_FOUND` (FR-010)

### Implementation for User Story 1

- [X] T010 [US1] Implement `StaffQueueBookingService.bookSlot` — NOT itself `@Transactional` (research.md); fee-resolution-first-write-gate ordering, `QueueSlotService.issueNextSlot` called directly (never inside a broader transaction this method opens); the final Booking-creation step is a separate `@Transactional` helper that **re-fetches the Slot by id** (`slotRepository.findById(slot.getId())`) rather than reusing `issueNextSlot`'s returned reference, since that reference is a detached entity by the time this step runs (research.md); plain `bookingRepository.save` (no race-closure catch needed, research.md) — in `backend/src/main/java/com/cms/booking/StaffQueueBookingService.java` (depends on T001, T004)
- [X] T011 [US1] Implement `StaffQueueBookingController` (`POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings`) per contracts/queue-booking.md in `backend/src/main/java/com/cms/booking/StaffQueueBookingController.java` (depends on T010)

**Checkpoint**: User Story 1 fully functional and independently testable.

---

## Phase 4: Patient Self-Service Books into a Queue Session (Priority: P2)

**Goal**: The patient-authenticated analog of US1, using `PatientLinkingService.findOrCreatePatient` for patient resolution exactly as 021 does.

**Independent Test**: Per quickstart.md Scenarios 3, 4, 5, 7 (patient half).

### Tests for User Story 2 (write first, confirm they FAIL before implementation)

- [X] T012 [P] [US2] Integration test: booking as an already-linked patient succeeds (201) with a `tokenNumber`, in `backend/src/test/java/com/cms/booking/integration/PatientQueueBookingTest.java`
- [X] T013 [P] [US2] Integration test (same file): a first-ever booking at a clinic creates/links a new Patient record (no-match branch and phone-match branch — two `@Test` methods)
- [X] T014 [P] [US2] Integration test (same file): no fee resolvable blocks the booking; an unauthenticated request is rejected `401`; a `FIXED_TIME`-mode Session is rejected `409 NOT_A_QUEUE_SESSION`

### Implementation for User Story 2

- [X] T015 [US2] Implement `PatientQueueBookingService.bookSlot` — same non-`@Transactional` calling convention and re-fetch-by-id pattern as T010, `PatientLinkingService.findOrCreatePatient` for patient resolution — in `backend/src/main/java/com/cms/booking/PatientQueueBookingService.java` (depends on T001, T004)
- [X] T016 [US2] Implement `PatientQueueBookingController` (`POST /api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings`) per contracts/queue-booking.md in `backend/src/main/java/com/cms/booking/PatientQueueBookingController.java` (depends on T015)

**Checkpoint**: Both user stories independently functional — the complete queue booking flow.

---

## Phase 5: Cross-Cutting Concurrency Verification

- [X] T017 [P] Integration test: N concurrent queue booking attempts (calling `StaffQueueBookingService.bookSlot` directly) against the same active Queue Session all succeed, each with a distinct, never-reused `tokenNumber` on its Slot — proves `QueueSlotService`'s retry closure still functions correctly when called the way T010 calls it (research.md) — in `backend/src/test/java/com/cms/booking/integration/QueueBookingConcurrencyTest.java` (depends on T010)

---

## Phase 6: Frontend & Polish

- [X] T018 [P] Create `frontend/src/features/staff-booking/queueApi.ts` (bearer-token fetch client for the staff endpoint)
- [X] T019 [P] Create `frontend/src/features/staff-booking/QueueBookSlotForm.tsx` — same existing-patient-id-or-new-walk-in-name/phone shape as `BookSlotForm.tsx`, shows the assigned token number on success
- [X] T020 [P] Create `frontend/src/features/patient-booking/queueApi.ts` (bearer-token fetch client for the patient endpoint)
- [X] T021 [P] Create `frontend/src/features/patient-booking/QueueBookSlotForm.tsx` — patient-name + appointment-type shape, shows the assigned token number on success
- [X] T022 [P] Frontend test: `frontend/tests/staff-booking/QueueBookSlotForm.test.tsx` (depends on T019)
- [X] T023 [P] Frontend test: `frontend/tests/patient-booking/QueueBookSlotForm.test.tsx` (depends on T021)
- [X] T024 Run `quickstart.md` Scenarios 1–7 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T025 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 013/015/016/019/021's existing tests

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational.
- **User Story 2 (Phase 4)**: Depends on Foundational — independent of US1's implementation (different service/controller), though its fixture is shared.
- **Concurrency (Phase 5)**: Depends on US1's T010.
- **Frontend & Polish (Phase 6)**: Depends on both stories.

### Parallel Opportunities

- T005–T009 (all US1 tests, same file) drafted together; T012–T014 (all US2 tests, same file) drafted together — both depend only on T004.
- T018–T021 in parallel (different files); T022, T023 in parallel once their respective components exist.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenarios 1, 2, 4, 5, 7 (staff half) pass.

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenario 3 passes.
3. Phase 5: concurrency verification.
4. Phase 6: frontend, full-suite verification, quickstart sign-off.

---

## Phase 7: Convergence

- [X] T026 Fix `StaffQueueBookingService`/`PatientQueueBookingService`: remove the separate `createBooking(...)` helper and its `@Transactional` annotation - self-invocation from `bookSlot` silently bypasses it (the same gotcha this feature's research.md already documents for `QueueSlotService.attemptIssueSlot`), so the intended "re-fetch to keep Slot attached" never actually happens. Inline `bookingRepository.save(new Booking(slot, patient, appointmentType, lockedFee, callerId))` directly in `bookSlot`, using `issueNextSlot`'s original `slot` reference (a detached-entity FK reference is fine for a simple, non-cascading INSERT, per research.md's own already-accepted reasoning) - in `backend/src/main/java/com/cms/booking/StaffQueueBookingService.java` and `PatientQueueBookingService.java` per research.md (contradicts)
