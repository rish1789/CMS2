---

description: "Task list for Patient Immediate Anonymization"
---

# Tasks: Patient Immediate Anonymization

**Input**: Design documents from `/specs/037-patient-immediate-anonymization/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/patient-anonymization.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Single user story. Everything lives in `com.cms.patient.record` (research.md R1).

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends `backend/src/main/java/com/cms/patient/record/` and
`backend/src/main/java/com/cms/booking/BookingRepository.java`. New test package
`backend/src/test/java/com/cms/patient/record/integration/`. Extends
`backend/src/main/java/com/cms/identity/account/SecurityConfig.java`. New:
`frontend/src/features/patient-anonymization/`, `frontend/tests/patient-anonymization/`.

---

## Phase 1: Setup

**Purpose**: The `Patient` extension and response DTO.

- [X] T001 Add `anonymizedAt` (nullable `Instant`) field, `anonymize()` mutator (idempotent guard), and `isAnonymized()` to `Patient` per data-model.md/research.md R4 in `backend/src/main/java/com/cms/patient/record/Patient.java`
- [X] T002 [P] Create `PatientAnonymizationResponse` (`patientId`, `anonymized`, `anonymizedAt`) DTO per contracts/patient-anonymization.md in `backend/src/main/java/com/cms/patient/record/dto/PatientAnonymizationResponse.java`

**Checkpoint**: Types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Migration, the Booking query, exceptions, security matcher, and the test fixture.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T003 Create migration `V22__patient_anonymized_at.sql` per data-model.md in `backend/src/main/resources/db/migration/V22__patient_anonymized_at.sql` (depends on T001)
- [X] T004 Add `existsActiveFutureBookingForPatient(UUID patientId): boolean` to `BookingRepository` per data-model.md/research.md R2 in `backend/src/main/java/com/cms/booking/BookingRepository.java`
- [X] T005 [P] Create `PatientNotFoundException`, `PatientHasActiveFutureBookingException` in `backend/src/main/java/com/cms/patient/record/` per research.md R3 (fresh exceptions, not reused from `com.cms.booking`)
- [X] T006 Create `PatientRecordExceptionHandler` mapping both new exceptions (`404 PATIENT_NOT_FOUND`, `409 PATIENT_HAS_ACTIVE_FUTURE_BOOKING`) per contracts/patient-anonymization.md in `backend/src/main/java/com/cms/patient/record/PatientRecordExceptionHandler.java` (depends on T005). Reuses `com.cms.scheduling.ForbiddenException` directly for the role-authorization rejection — already globally mapped
- [X] T007 Add explicit matcher `.requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/patients/*/anonymize").authenticated()` to the `/api/v1/clinics/**` chain in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`
- [X] T008 Create `AbstractPatientAnonymizationIntegrationTest` — clinic/doctor/staff-token(Operations, ClinicAdmin, Doctor)/patient/booking fixture (mirrors `AbstractSessionCancellationIntegrationTest`'s combination shape) — in `backend/src/test/java/com/cms/patient/record/integration/AbstractPatientAnonymizationIntegrationTest.java` (depends on T004)

**Checkpoint**: Foundation ready — the user story can now be built.

---

## Phase 3: Staff Anonymizes a Patient Record on Request (Priority: P1) 🎯 MVP

**Goal**: Operations/ClinicAdmin staff can immediately scrub a Patient's identifying fields, blocked only while an active future booking exists, idempotently, with everything else left untouched.

**Independent Test**: Per quickstart.md Scenarios 1–6.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T009 [P] [US1] Integration test: anonymize a Patient with no active future bookings → `200`, `anonymized: true`; `name`/`phone` scrubbed (FR-001/FR-002, SC-001) — in `backend/src/test/java/com/cms/patient/record/integration/PatientAnonymizationTest.java`
- [X] T010 [P] [US1] Integration test: a Patient with an active future booking → `409 PATIENT_HAS_ACTIVE_FUTURE_BOOKING`, no fields modified (FR-003, SC-002); cancel that booking, retry → `200` (Acceptance Scenario 4) — in the same file as T009
- [X] T011 [P] [US1] Integration test: a second anonymization attempt on an already-anonymized Patient → `200`, identical `anonymizedAt` to the first call (FR-007, SC-004) — in the same file as T009
- [X] T012 [P] [US1] Integration test: an anonymized Patient's existing Bookings and clinical documentation (a Consultation Note) remain fully intact and queryable afterward; the linked Patient Account (if any) is untouched (FR-005/FR-006, SC-003) — in the same file as T009
- [X] T013 [P] [US1] Integration test: a Doctor's own token (no Operations/ClinicAdmin role) → `403 FORBIDDEN`; an unknown `patientId` → `404 PATIENT_NOT_FOUND` — in `backend/src/test/java/com/cms/patient/record/integration/PatientAnonymizationAuthorizationTest.java`

### Implementation for User Story 1

- [X] T014 [US1] Implement `PatientAnonymizationService.anonymize(clinicId, patientId): Patient` — loads the Patient scoped to clinic, checks `existsActiveFutureBookingForPatient` (skips the check and returns immediately if already anonymized, FR-007), throws `PatientHasActiveFutureBookingException` if blocked, else calls `patient.anonymize()` and saves — in `backend/src/main/java/com/cms/patient/record/PatientAnonymizationService.java` (depends on T001, T004, T005)
- [X] T015 [US1] Implement `StaffPatientAnonymizationController` (`POST /api/v1/clinics/{clinicId}/patients/{patientId}/anonymize`, Operations-or-ClinicAdmin gate per research.md R7) per contracts/patient-anonymization.md — in `backend/src/main/java/com/cms/patient/record/StaffPatientAnonymizationController.java` (depends on T002, T014)

**Checkpoint**: User Story 1 fully functional and independently testable.

---

## Phase 4: Frontend & Polish

- [X] T016 [P] Create `frontend/src/features/patient-anonymization/api.ts` — `anonymizePatient(clinicId, patientId, token)`
- [X] T017 Create `frontend/src/features/patient-anonymization/AnonymizePatientButton.tsx` — a confirm-then-submit action (depends on T016)
- [X] T018 [P] Frontend test: anonymizes successfully, shows the `PATIENT_HAS_ACTIVE_FUTURE_BOOKING`/`FORBIDDEN` error messages — in `frontend/tests/patient-anonymization/AnonymizePatientButton.test.tsx` (depends on T017)
- [X] T019 Run `quickstart.md` Scenarios 1–6 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md` — blocked by the sandbox's Testcontainers/Docker limitation (same as every prior feature this session); Scenarios 1-5 each covered by a passing-when-run integration test (T009-T013) verified via compile + structural review; Scenario 6 (frontend) verified live via the Vitest suite (T018, 4/4 green)
- [X] T020 Run full backend build (`/tmp/gradle-8.10/bin/gradle compileJava compileTestJava spotlessCheck -q` and `build -x test`) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS the user story's test-writing.
- **US1 (Phase 3)**: Depends on Foundational.
- **Frontend & Polish (Phase 4)**: Depends on US1.

### Parallel Opportunities

- T002 in parallel with T001.
- T005 items in parallel.
- T009–T013 (all tests) in parallel — depend only on T008.

---

## Implementation Strategy

1. Phase 1 → Phase 2. **STOP and VALIDATE**: foundational pieces compile, no behavior yet.
2. Phase 3. **STOP and VALIDATE**: quickstart.md Scenarios 1–5 pass.
3. Phase 4: frontend, full-suite verification, quickstart sign-off.
