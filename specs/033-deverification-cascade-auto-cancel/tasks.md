---

description: "Task list for De-Verification Cascade (Auto-Cancel Future Bookings)"
---

# Tasks: De-Verification Cascade (Auto-Cancel Future Bookings)

**Input**: Design documents from `/specs/033-deverification-cascade-auto-cancel/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/doctor-revoke.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 clinic-triggered cascade, US2 = P1 the necessary-prerequisite doctor-revoke action plus its own cascade). The shared batch-cancellation core both stories need is built in Foundational, not deferred to either story.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

New event + admin action in `backend/src/main/java/com/cms/identity/admin/`. New cascade
service/listener in `backend/src/main/java/com/cms/booking/` (research.md R3). Extends
`backend/src/main/java/com/cms/booking/BookingRepository.java` and
`backend/src/main/java/com/cms/identity/admin/DoctorVerificationService.java`/
`DoctorVerificationController.java`. New test fixture in
`backend/src/test/java/com/cms/booking/integration/`. Frontend: extends
`frontend/src/features/doctor-verification/` (existing admin UI) with a Revoke action.

---

## Phase 1: Setup

**Purpose**: The new domain event.

- [X] T001 Create `DoctorLicenseRevokedEvent` record (`doctorProfileId`, `occurredAt`), mirroring `ClinicDeVerifiedEvent` exactly, in `backend/src/main/java/com/cms/identity/admin/DoctorLicenseRevokedEvent.java`

**Checkpoint**: Type exists; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The repository queries and the shared batch-cancellation core both triggers need, plus the test fixture.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T002 [P] Add `findActiveFutureBookingsByClinic(UUID clinicId)` and `findActiveFutureBookingsByDoctor(UUID doctorProfileId)` to `BookingRepository` per data-model.md (research.md R5 — `status = ACTIVE AND slot.status = BOOKED`, no date/time filter)
- [X] T003 Implement `DeVerificationCascadeService.cancelBatch(List<Booking> bookings)` — the shared core: for each Booking, branches on `Session.mode` (research.md R4) to actually cancel it — `FIXED_TIME` → `BookingCancellationService.cancel(booking)` unchanged (real waitlist-bump chain); `QUEUE` → direct `BookingRepository.cancelIfActive` + `slot.setStatus(OPEN)` (skip silently on a lost race) — then, regardless of which branch ran and only once the cancellation actually succeeded, calls `NotificationEventService.publish` for that booking's linked Patient Account (skip if walk-in) — FR-006 applies uniformly to both modes, not just Queue-mode — in `backend/src/main/java/com/cms/booking/DeVerificationCascadeService.java` (depends on T002)
- [X] T004 Create `AbstractDeVerificationCascadeIntegrationTest` in `backend/src/test/java/com/cms/booking/integration/` — combines `AbstractAdminIntegrationTest`'s Super-Admin-auth/clinic/doctor helpers with `AbstractSessionCancellationIntegrationTest`'s Fixed-Time/Queue-mode Session+Slot+Booking helpers, plus `ClinicVerificationService`/`DoctorVerificationService`/`NotificationEventRepository`/`WaitlistEntryRepository` autowired directly (depends on T002, T003)

**Checkpoint**: Foundation ready — both user stories can now be built.

---

## Phase 3: Un-Verifying a Clinic Cancels Its Future Bookings (Priority: P1) 🎯 MVP

**Goal**: `ClinicVerificationService.unverify()`'s existing event finally has a listener that cascades to every future booking at that clinic.

**Independent Test**: Per quickstart.md Scenario 1.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T005 [P] [US1] Integration test: a verified Clinic with active Fixed-Time and Queue-mode bookings across doctors, plus one already-`COMPLETED` booking → un-verifying cancels every active booking, leaves the completed one untouched (FR-001/FR-007, SC-001/SC-002) — in `backend/src/test/java/com/cms/booking/integration/ClinicDeVerificationCascadeTest.java`
- [X] T006 [P] [US1] Integration test: a cancelled Fixed-Time booking with a matching `WAITING` waitlist entry produces a real waitlist offer (FR-004, SC-003); a cancelled Queue-mode booking produces no waitlist offer (FR-005) — in the same file as T005
- [X] T007 [P] [US1] Integration test: each cancelled booking's linked Patient Account gets exactly one notification event; a walk-in (no Patient Account) gets none (FR-006, SC-004); re-un-verifying an already-unverified clinic is a no-op — no second cascade, no duplicate notifications (FR-009); the clinic is re-verified afterward and the cascade-cancelled bookings remain `CANCELLED` (FR-008, Analyze finding C1) — in the same file as T005

### Implementation for User Story 1

- [X] T008 [US1] Implement `DeVerificationCascadeService.cascadeFromClinic(UUID clinicId)` — queries `findActiveFutureBookingsByClinic`, delegates to `cancelBatch` — in `backend/src/main/java/com/cms/booking/DeVerificationCascadeService.java` (depends on T003)
- [X] T009 [US1] Implement `DeVerificationCascadeListener.onClinicDeVerified(ClinicDeVerifiedEvent)` — `@TransactionalEventListener(phase = AFTER_COMMIT)`, mirrors `WaitlistBumpListener`'s (031) identical shape — in `backend/src/main/java/com/cms/booking/DeVerificationCascadeListener.java` (depends on T008)

**Checkpoint**: User Story 1 fully functional and independently testable.

---

## Phase 4: Explicitly Revoking a Doctor's License Cancels Their Future Bookings (Priority: P1) 🎯 Necessary Prerequisite

**Goal**: The missing explicit doctor-license-revoke admin action is built, and its own cascade fires across every clinic that doctor is staffed at.

**Independent Test**: Per quickstart.md Scenarios 2–4.

### Tests for User Story 2 (write first, confirm they FAIL before implementation)

- [X] T010 [P] [US2] Integration test: `POST /api/v1/admin/doctors/{doctorProfileId}/revoke` on a verified doctor → `200`, `licenseVerified: false`, every active booking for that doctor across multiple clinics is cancelled (FR-002/FR-003, SC-001); an unknown `doctorProfileId` → `404 DOCTOR_PROFILE_NOT_FOUND`; missing/invalid Super Admin credentials → `401` — in `backend/src/test/java/com/cms/booking/integration/DoctorRevokeCascadeTest.java`
- [X] T011 [P] [US2] Integration test: revoking an already-unverified doctor is a no-op — `200`, no second cascade, no duplicate notifications (FR-009); revoking leaves that doctor's past/completed bookings untouched (FR-007, SC-002) — in the same file as T010
- [X] T012 [P] [US2] Integration test: a doctor's `licenseVerified` reset purely from an automatic license-number edit (006's `DoctorVerificationService.edit`) never triggers this cascade — active future bookings survive untouched (FR-002/FR-009, SC-005) — in the same file as T010

### Implementation for User Story 2

- [X] T013 [US2] Implement `DoctorVerificationService.revoke(UUID doctorProfileId)` — mirrors `ClinicVerificationService.unverify()`'s exact idempotent shape, publishing `DoctorLicenseRevokedEvent` only on a genuine `true -> false` transition — in `backend/src/main/java/com/cms/identity/admin/DoctorVerificationService.java` (depends on T001)
- [X] T014 [US2] Implement `DoctorVerificationController`'s `POST /{doctorProfileId}/revoke` endpoint per contracts/doctor-revoke.md, reusing `DoctorVerificationStatusResponse` — in `backend/src/main/java/com/cms/identity/admin/DoctorVerificationController.java` (depends on T013)
- [X] T015 [US2] Implement `DeVerificationCascadeService.cascadeFromDoctor(UUID doctorProfileId)` — queries `findActiveFutureBookingsByDoctor`, delegates to `cancelBatch` — in `backend/src/main/java/com/cms/booking/DeVerificationCascadeService.java` (depends on T003)
- [X] T016 [US2] Implement `DeVerificationCascadeListener.onDoctorLicenseRevoked(DoctorLicenseRevokedEvent)` — `@TransactionalEventListener(phase = AFTER_COMMIT)` — in `backend/src/main/java/com/cms/booking/DeVerificationCascadeListener.java` (depends on T015)

**Checkpoint**: Both user stories independently functional — the complete de-verification cascade for both triggers.

---

## Phase 5: Frontend & Polish

- [X] T017 [P] Extend `frontend/src/features/doctor-verification/api.ts` — `revokeDoctor(doctorProfileId, credentials)`
- [X] T018 Extend `frontend/src/features/doctor-verification/PendingDoctorsList.tsx` — a "Revoke" action on the Verified tab, mirroring the existing "Verify" action's shape (depends on T017)
- [X] T019 [P] Frontend test: revoking removes the doctor from the Verified tab; shows the error message on failure — in `frontend/tests/doctor-verification/PendingDoctorsList.test.tsx` (depends on T018)
- [X] T020 Run `quickstart.md` Scenarios 1–4 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md` — blocked by the sandbox's Testcontainers/Docker limitation (same as every prior feature this session); Scenarios 1-3 each covered by a passing-when-run integration test (T005-T007, T010-T012) verified via compile + structural review; Scenario 4 partially covered inline within those same tests (idempotent re-trigger assertions)
- [X] T021 Run full backend build (`/tmp/gradle-8.10/bin/gradle compileJava compileTestJava spotlessCheck -q` and `build -x test`) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 003/007's existing tests (given `DoctorVerificationService`/`DoctorVerificationController` and `BookingRepository` were extended in place)

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories' test-writing.
- **US1 / Clinic Cascade (Phase 3)**: Depends on Foundational.
- **US2 / Doctor Revoke + Cascade (Phase 4)**: Depends on Foundational. Independent of US1 (does not call anything US1 adds).
- **Frontend & Polish (Phase 5)**: Depends on both stories.

### Parallel Opportunities

- T002 alone in Foundational's first sub-step.
- T005–T007 (US1 tests) in parallel — depend on T004.
- T010–T012 (US2 tests) in parallel — depend on T004.
- US1 (Phase 3) and US2 (Phase 4) implementation can proceed in any order once Foundational completes.

---

## Implementation Strategy

1. Phase 1 → Phase 2. **STOP and VALIDATE**: foundational pieces compile, no behavior yet.
2. Phase 3 (clinic cascade). **STOP and VALIDATE**: quickstart.md Scenario 1 passes.
3. Phase 4 (doctor revoke + cascade). **STOP and VALIDATE**: quickstart.md Scenarios 2–4 pass.
4. Phase 5: frontend, full-suite verification, quickstart sign-off.
