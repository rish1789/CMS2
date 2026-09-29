---
description: "Task list for 062-rejected-clinic-gating"
---

# Tasks: Rejected Clinics Stop Operating

**Input**: Design documents from `specs/062-rejected-clinic-gating/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/rejected-clinic-gating.md, quickstart.md

**Tests**: REQUIRED. Constitution Principle I (test-first) applies. Every implementation task is preceded by a failing test.

**Environment**:
- Backend: `"/c/Users/risha/AppData/Local/Temp/gradle-8.10/bin/gradle.bat" -p backend ...`. Stop the running backend and `rm -rf backend/build` if Gradle reports "not a regular file" (OneDrive sync corruption).
- Integration tests: Testcontainers, Docker-gated in this sandbox. Write them and make them compile; they are verified in a real dev/CI environment.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

- [x] T001 Confirm no migration is needed: grep `backend/src/main/resources/db/migration` for any check constraint on `booking.cancellation_reason` or `waitlist_entry.status`. Expected none (V31 adds a plain `VARCHAR(50)`). If one exists, stop and add a forward-only migration task before T004.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The event and the two shared error types that US1, US2 and US4 all depend on.

- [x] T002 Write failing unit test `backend/src/test/java/com/cms/identity/admin/unit/ClinicRejectedEventPublicationTest.java` (Mockito, same constructor wiring as `RejectedClinicDeletionGuardTest`):
  - rejecting a pending clinic publishes exactly one `ClinicRejectedEvent` with its id;
  - rejecting an already-rejected clinic publishes none;
  - `rejectBulk` over 2 pending clinics publishes 2.
- [x] T003 Create `backend/src/main/java/com/cms/identity/admin/domain/ClinicRejectedEvent.java`, a record `(UUID clinicId, Instant occurredAt)` with `of(UUID)`, mirroring `ClinicDeVerifiedEvent`. Publish it from `ClinicVerificationService.rejectOne` only inside the `!clinic.isRejected()` branch, after `save`. T002 goes green.
- [x] T004 [P] Add `CLINIC_REJECTED` to `backend/src/main/java/com/cms/booking/domain/BookingCancellationReason.java`, with a Javadoc that it is system-only (set by the rejection cascade, never accepted as caller input).
- [x] T005 [P] Create `backend/src/main/java/com/cms/booking/exception/ClinicNotAcceptingAppointmentsException.java` and map it in `BookingExceptionHandler` to `409 {"error":"CLINIC_NOT_ACCEPTING_APPOINTMENTS","message":"This clinic is not accepting appointments."}` (contract §1). `BookingExceptionHandler` is a global `@RestControllerAdvice`, so this one mapping also covers the waitlist claim controller (analyze A1).

**Checkpoint**: event published on reject; shared error types exist.

---

## Phase 3: User Story 1 - No appointment can be booked at a rejected clinic (Priority: P1) 🎯 MVP

**Goal**: Every booking path refuses a rejected clinic (FR-001–FR-003). Rejection cancels upcoming bookings with a patient-visible reason, without waitlist offers (FR-009, FR-010), and closes the clinic's waitlist (FR-011).

**Independent Test**: quickstart.md Scenarios 1–3.

### Tests for User Story 1 (write first, confirm red)

- [x] T006 [P] [US1] Unit tests, one per booking service. A rejected clinic's slot/session → `ClinicNotAcceptingAppointmentsException`, and no `bookingRepository.save*`, patient-linking, or fee-resolution call is made. A pending clinic → unchanged. Files:
  - `backend/src/test/java/com/cms/booking/unit/RejectedClinicBookingRefusalTest.java`, covering `PatientBookingService.bookSlot`, `PatientQueueBookingService`, `StaffBookingService.bookSlot`, `StaffQueueBookingService.bookSlot`, `WalkInInsertionService.insertWalkIn`.
  - Use a `@Nested` class per service if constructor wiring differs.
- [x] T007 [P] [US1] Contract test `backend/src/test/java/com/cms/booking/contract/ClinicNotAcceptingAppointmentsContractTest.java`: the patient fixed-time book endpoint with the service mocked to throw → `409 CLINIC_NOT_ACCEPTING_APPOINTMENTS` with the contract §1 body. Also the patient cancel endpoint with `reason: "CLINIC_REJECTED"` → `400 INVALID_CANCELLATION_REASON`.
- [x] T008 [P] [US1] Unit test `backend/src/test/java/com/cms/booking/unit/ClinicRejectionCascadeServiceTest.java`:
  - each upcoming booking is cancelled via `cancelIfActive(id, CLINIC_REJECTED, null)`;
  - its slot is set `OPEN`;
  - one `BOOKING_CANCELLED_CLINIC_REJECTED` notification is published per booking with a patient account, none for walk-ins without one;
  - `BookingCancellationService` is never invoked (no waitlist bump);
  - a lost race (`cancelIfActive` returns 0) skips that booking without error.
- [x] T009 [P] [US1] Integration test `backend/src/test/java/com/cms/booking/integration/ClinicRejectionCascadeTest.java` (extends `AbstractDeVerificationCascadeIntegrationTest` or its sibling base). Setup: a pending clinic with a future BOOKED fixed-time booking, a past-dated BOOKED booking, an APPEARED booking today, and a WAITING waitlist entry. Reject via `POST /api/v1/admin/clinics/reject-bulk`. Assert:
  - the future booking is CANCELLED/`CLINIC_REJECTED` and its slot OPEN;
  - the past and APPEARED bookings are untouched;
  - no waitlist entry became OFFERED;
  - the waiting entry is EXPIRED;
  - every assertion reads through a fresh repository query after the reject request returns, proving the AFTER_COMMIT writes actually persisted (analyze C1).
- [x] T010 [P] [US1] Integration test `backend/src/test/java/com/cms/booking/integration/RejectedClinicBookingRefusalTest.java`: with the clinic rejected, each real endpoint returns 409 and writes no booking:
  - patient fixed-time book;
  - patient queue book;
  - staff fixed-time and staff queue book, with the ClinicAdmin as caller;
  - walk-in;
  - waitlist claim of an offer made before rejection.
- [x] T011 [P] [US1] Frontend test in `frontend/tests/patient-bookings/MyBookings.test.tsx`: a CANCELLED booking with `cancellationReason: 'CLINIC_REJECTED'` shows the line "This clinic is no longer accepting appointments." under its "Cancelled" badge; any other cancelled booking shows only the badge.

### Implementation for User Story 1

- [x] T012 [US1] Add the check at the top of each of the 5 services' booking methods, right after the Slot/Session is loaded and before any write: if `session.getClinic().isRejected()`, throw `ClinicNotAcceptingAppointmentsException`. Files:
  - `backend/src/main/java/com/cms/booking/service/PatientBookingService.java`
  - `PatientQueueBookingService.java`
  - `StaffBookingService.java`
  - `StaffQueueBookingService.java`
  - `WalkInInsertionService.java`

  T006 goes green.
- [x] T013 [US1] In `backend/src/main/java/com/cms/booking/api/PatientBookingCancellationController.java`, reject `CLINIC_REJECTED` as caller input with the existing `InvalidCancellationReasonException`.
- [x] T014 [US1] Add `findActiveUpcomingBookingsByClinic(UUID clinicId, LocalDate today)` to `backend/src/main/java/com/cms/booking/repository/BookingRepository.java`: ACTIVE booking, slot `BOOKED`, `slot.session.clinic.id = :clinicId`, `slot.session.sessionDate >= :today`.
- [x] T015 [US1] Create `backend/src/main/java/com/cms/booking/service/ClinicRejectionCascadeService.java` (`@Transactional(propagation = REQUIRES_NEW) cascadeFromClinic(UUID)` — REQUIRES_NEW is mandatory: it runs from an AFTER_COMMIT listener, where a REQUIRED transaction joins the already-committed one and its writes silently never persist (analyze C1). Per research.md Decision 4, event type `BOOKING_CANCELLED_CLINIC_REJECTED`). Also create `ClinicRejectionCascadeListener.java` (`@TransactionalEventListener(AFTER_COMMIT)` on `ClinicRejectedEvent`), mirroring `DeVerificationCascadeListener`. T008 goes green.
- [x] T016 [US1] Add a `@Modifying` bulk update `expireOpenByClinic(UUID clinicId)` (WAITING/OFFERED → EXPIRED) to `backend/src/main/java/com/cms/waitlist/repository/WaitlistEntryRepository.java`. Create `backend/src/main/java/com/cms/waitlist/service/ClinicRejectionWaitlistListener.java` (AFTER_COMMIT, `@Transactional(propagation = REQUIRES_NEW)` since it writes after commit), which first calls the existing `InboxItemService.resolveByWaitlistEntry` for each OFFERED entry at the clinic, then runs the bulk expire (analyze A2).
- [x] T017 [US1] Add `BookingCancellationReason cancellationReason` to `backend/src/main/java/com/cms/booking/dto/PatientBookingSummaryResponse.java` and its `from(...)` mapping.
- [x] T018 [US1] Frontend:
  - add `cancellationReason: string | null` to `PatientBookingSummary` in `frontend/src/features/patient-bookings/api.ts`;
  - render the rejection message in `frontend/src/features/patient-bookings/MyBookings.tsx` (T011 goes green);
  - add a `CLINIC_NOT_ACCEPTING_APPOINTMENTS` → "This clinic is not accepting appointments." case to every booking client's error-message mapping: `frontend/src/features/patient-booking/{api,queueApi}.ts`, `frontend/src/features/staff-booking/{api,queueApi}.ts`, and the waitlist claim client in `frontend/src/features/waitlist/api.ts`.

**Checkpoint**: US1 is fully functional. Rejection cancels and refuses, and the patient sees why.

---

## Phase 4: User Story 2 - A rejected clinic stops generating new sessions (Priority: P2)

**Goal**: FR-004. **Independent Test**: quickstart.md Scenario 4.

- [x] T019 [P] [US2] Unit test `backend/src/test/java/com/cms/scheduling/unit/ScheduleSessionGeneratorRejectedClinicTest.java`: `generateForSchedule` for a schedule whose clinic is rejected returns 0 and saves no session; for a pending clinic it generates as before.
- [x] T020 [P] [US2] Integration test `backend/src/test/java/com/cms/scheduling/integration/SessionGenerationSkipsRejectedClinicTest.java`: two clinics with schedules, one rejected. The manual trigger creates sessions only for the other.
- [x] T021 [US2] In `backend/src/main/java/com/cms/scheduling/service/ScheduleSessionGenerator.java` `generateForSchedule`, return 0 right after loading the schedule when `schedule.getClinic().isRejected()`. T019 goes green.

**Checkpoint**: No generation for rejected clinics.

---

## Phase 5: User Story 4 - Only the clinic admin keeps access to a rejected clinic (Priority: P2)

**Goal**: FR-007. **Independent Test**: quickstart.md Scenario 5.

### Tests for User Story 4 (write first, confirm red)

- [x] T022 [P] [US4] Unit tests in `backend/src/test/java/com/cms/identity/account/StaffAuthServiceTest.java` (extend the existing file):
  - an account whose only active role is Doctor at a rejected clinic → `StaffClinicNotActiveException`;
  - ClinicAdmin at a rejected clinic → token issued;
  - Doctor at rejected + Doctor at pending → token issued;
  - no role assignments → token issued (unchanged);
  - Super Admin → unchanged.
- [x] T023 [P] [US4] Unit test `backend/src/test/java/com/cms/identity/account/unit/RejectedClinicAccessGateTest.java` for the gate's decision:
  - clinic not rejected → allow, with no role lookup;
  - rejected + active ClinicAdmin → allow;
  - rejected + Doctor only → deny;
  - rejected + no role → allow (not this gate's concern: the existing per-service "not staffed" check keeps answering it).
- [x] T024 [P] [US4] Contract test in `backend/src/test/java/com/cms/identity/account/contract/StaffAuthControllerTest.java`: a login throwing `StaffClinicNotActiveException` → `403 {"error":"CLINIC_NOT_ACTIVE", ...}` (contract §3).
- [x] T025 [P] [US4] Integration test `backend/src/test/java/com/cms/identity/account/integration/RejectedClinicStaffAccessTest.java`:
  - Doctor login refused;
  - the Doctor's pre-rejection token on `GET /api/v1/clinics/{id}/...` (e.g. the doctors list) → 403 `CLINIC_NOT_ACTIVE`;
  - ClinicAdmin login succeeds, and `/api/v1/clinics/mine` still lists the clinic;
  - the Doctor's `/mine` omits it;
  - after restore the Doctor's login succeeds.
- [x] T026 [P] [US4] Frontend test in `frontend/tests/staff-login/StaffLoginForm.test.tsx`: a `403 CLINIC_NOT_ACTIVE` response shows the server's message.

### Implementation for User Story 4

- [x] T027 [US4] Create `backend/src/main/java/com/cms/identity/account/exception/StaffClinicNotActiveException.java`. Map it to `403 CLINIC_NOT_ACTIVE` in the staff-auth exception handler, the one that already maps `IncorrectPasswordException`.
- [x] T028 [US4] In `backend/src/main/java/com/cms/identity/account/service/StaffAuthService.java` `login`, after the password match, load the account's active role assignments. If there is at least one and every one is non-ClinicAdmin at a rejected clinic, throw. T022 goes green.
- [x] T029 [US4] Create `backend/src/main/java/com/cms/identity/account/service/RejectedClinicAccessGate.java` (`boolean allows(UUID accountId, UUID clinicId)`, per T023). Create `backend/src/main/java/com/cms/identity/account/config/RejectedClinicAccessInterceptor.java`: resolve `clinicId` from `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE`; act only when the current authentication is a staff principal; on deny, write the 403 `CLINIC_NOT_ACTIVE` JSON body. Create `RejectedClinicAccessWebConfig.java` (a `WebMvcConfigurer` registering the interceptor for `/api/v1/clinics/**`, obtaining the gate through `ObjectProvider` so `@WebMvcTest` slices without it stay inert). T023 goes green.
- [x] T030 [US4] Add a membership query to `backend/src/main/java/com/cms/identity/account/repository/RoleAssignmentRepository.java`: active assignments for an account, excluding those where the clinic is rejected and the role is not ClinicAdmin, paged. Use it in `backend/src/main/java/com/cms/identity/account/api/StaffClinicController.java` `mine`.
- [x] T031 [US4] Frontend: add `{ error: 'CLINIC_NOT_ACTIVE'; message?: string }` to `LoginStaffErrorBody` in `frontend/src/features/staff-login/api.ts`. The form already renders `body.message`, so T026 goes green.

**Checkpoint**: Staff access limited to ClinicAdmin at rejected clinics.

---

## Phase 6: User Story 3 - Restoring a rejected clinic resumes normal operation (Priority: P3)

**Goal**: FR-005. **Independent Test**: quickstart.md Scenario 6.

- [x] T032 [P] [US3] Integration test `backend/src/test/java/com/cms/booking/integration/RestoredClinicResumesTest.java`: reject → restore, then:
  - a patient fixed-time booking succeeds;
  - the manual generation trigger creates sessions for the full horizon;
  - bookings cancelled by the rejection remain CANCELLED;
  - the expired waitlist entry remains EXPIRED.
- [x] T033 [US3] Verify no restore-specific code is needed: every rule reads the live `Clinic.rejected` flag, and generation is idempotent per (schedule, date), per research.md Decision 2. If T032 exposes a gap, fix it at its source.

**Checkpoint**: Reject/restore round-trip is clean.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T034 Backend: `spotlessApply`, then run the full unit + contract suite (`test --tests "*.unit.*" --tests "*.contract.*"`), with zero regressions (FR-006/SC-004). Confirm every existing `@WebMvcTest` slice still starts, proving the `ObjectProvider` isolation.
- [x] T035 [P] Frontend: `npx tsc -b`, `npm run lint` (no new findings in touched files), `npx vitest run` (full suite green).
- [x] T036 Grep-verify FR-008 (every new check reads only the clinic its own entity or path names) and FR-001 completeness: the only `bookingRepository.save*` sites are the 5 guarded services, and waitlist claim reaches booking only via `PatientBookingService.bookSlot`.
- [x] T037 Live-verify quickstart.md Scenarios 1–6 against the restarted dev stack, using throwaway data only. Record the results here.
  - **Results (2026-09-24, throwaway clinic "Gating Verify 1790252287")**:
    - **S1**: both upcoming bookings CANCELLED/`CLINIC_REJECTED`, slots OPEN. One `BOOKING_CANCELLED_CLINIC_REJECTED` notification went out (the walk-in has no account). My bookings shows the explanation line. The AFTER_COMMIT writes persisted (analyze C1 confirmed live).
    - **S2**: the waiting entry is EXPIRED.
    - **S3**: patient book and ClinicAdmin staff book both return 409 `CLINIC_NOT_ACCEPTING_APPOINTMENTS`.
    - **S4**: the manual generate run while rejected created 0 sessions.
    - **S5**: Doctor login → 403 `CLINIC_NOT_ACTIVE`; the Doctor's pre-rejection token on `/clinics/{id}/doctors` → 403; ClinicAdmin login 200; `/mine` count is 1 for the admin and 0 for the doctor.
    - **S6**: after restore, Doctor login 200 and patient book 201; the rejection-cancelled booking stays CANCELLED.
    - **Not run here**: the Testcontainers integration classes (T009/T010/T020/T025/T032). Docker Desktop is not running in this environment; they compile and are verified in a real dev/CI environment.

---

## Dependencies & Execution Order

- **Setup (T001)**: runs first. It gates whether a migration task must be inserted.
- **Foundational (T002–T005)**: blocks every story. US1 needs the event plus T004/T005; US4 is independent of T004/T005 but runs after them for simplicity.
- **Story order**:
  - US1 (P1) must come first: it is the MVP.
  - US2 and US4 (both P2) are independent of each other and of US1's implementation files. They can run in parallel.
  - US3 (P3) depends on US1 + US2 + US4, since it verifies all of them lift on restore.
- **Within each story**: tests first (confirm red), then implementation in the listed order. T012 touches 5 files, so it runs sequentially after T006.
- **Polish (T034–T037)**: runs after all stories.

## Parallel Opportunities

- T004 ∥ T005 (after T003).
- US1 tests T006–T011 in parallel. They are different files.
- US2 (T019–T021) ∥ US4 (T022–T031) once Phase 2 is done.
- T034 ∥ T035.

## Implementation Strategy

- **MVP**: Phases 1–3 (US1). Rejected clinics can no longer take appointments, existing ones are cancelled with a patient-visible reason, and the waitlist is closed.
- **Increment 2**: US2 + US4. Generation stops; staff access is limited to ClinicAdmin.
- **Increment 3**: US3 restore verification. Then Polish and the live quickstart.
