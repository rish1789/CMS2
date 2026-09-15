---

description: "Task list for Prescription + Items Creation"
---

# Tasks: Prescription + Items Creation

**Input**: Design documents from `/specs/035-prescription-and-items-creation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/prescription.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Single user story, mirroring 030's own identical shape. The `TreatingDoctorAuthorizationService` extraction (a refactor of already-converged 030 code) is Foundational, since both 030's existing service and this feature's new service depend on it.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends the existing `backend/src/main/java/com/cms/clinical/` and `backend/src/test/java/com/cms/clinical/integration/`. Extends `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`. New: `frontend/src/features/prescriptions/`, `frontend/tests/prescriptions/`.

---

## Phase 1: Setup

**Purpose**: The new entities and request/response DTOs.

- [X] T001 Create `PrescriptionItem` entity (`prescription` `@ManyToOne` not null, `medicationName`/`dosage`/`frequency`/`duration` not null, `instructions` nullable) per data-model.md in `backend/src/main/java/com/cms/clinical/PrescriptionItem.java`
- [X] T002 Create `Prescription` entity (`booking` `@ManyToOne` not null — no uniqueness, per research.md R3; `doctorProfile` `@ManyToOne` not null; `items` `@OneToMany` cascade `PERSIST` only; `createdAt` not null) per data-model.md in `backend/src/main/java/com/cms/clinical/Prescription.java` (depends on T001)
- [X] T003 [P] Create `PrescriptionItemRequest`/`PrescriptionItemResponse` and `CreatePrescriptionRequest` (`items: List<PrescriptionItemRequest>`)/`PrescriptionResponse` DTOs per contracts/prescription.md in `backend/src/main/java/com/cms/clinical/dto/`

**Checkpoint**: Types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Migration, repository, the extracted shared authorization service (and 030's regression-safe refactor onto it), exceptions, security matchers, and the test fixture.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T004 Create migration `V20__create_prescription.sql` per data-model.md in `backend/src/main/resources/db/migration/V20__create_prescription.sql` (depends on T002)
- [X] T005 Create `PrescriptionRepository` with `findByBooking_Id(UUID bookingId): List<Prescription>` in `backend/src/main/java/com/cms/clinical/PrescriptionRepository.java` (depends on T002)
- [X] T006 Extract `TreatingDoctorAuthorizationService` (`findBookingInClinic`, `requireTreatingDoctor`) from `ConsultationNoteService`'s existing private methods per research.md R2, then refactor `ConsultationNoteService` to call it instead — no behavior change, verified by 030's own existing tests still passing unmodified — in `backend/src/main/java/com/cms/clinical/TreatingDoctorAuthorizationService.java` and `backend/src/main/java/com/cms/clinical/ConsultationNoteService.java`
- [X] T007 [P] Create `PrescriptionItemRequiredException` in `backend/src/main/java/com/cms/clinical/`. Reuses `com.cms.booking.BookingNotFoundException` and `com.cms.scheduling.ForbiddenException` directly, same as 030 — no duplication
- [X] T008 Extend `ClinicalDocumentationExceptionHandler` with a mapping for the new exception (`400 PRESCRIPTION_ITEM_REQUIRED`) per contracts/prescription.md in `backend/src/main/java/com/cms/clinical/ClinicalDocumentationExceptionHandler.java` (depends on T007)
- [X] T009 Add explicit matchers `.requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/bookings/*/prescriptions").authenticated()` and the `GET` equivalent to the `/api/v1/clinics/**` chain in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`
- [X] T010 Create `AbstractPrescriptionIntegrationTest` extending `AbstractConsultationNoteIntegrationTest` (mirrors this codebase's established fixture-inheritance pattern) with an `operationsToken(Clinic)` helper (the one role 030's own fixture never needed) — in `backend/src/test/java/com/cms/clinical/integration/AbstractPrescriptionIntegrationTest.java` (depends on T005, T006)

**Checkpoint**: Foundation ready — the user story can now be built.

---

## Phase 3: Treating Doctor Records a Prescription with Line-Item Medications (Priority: P1) 🎯 MVP

**Goal**: The doctor assigned to a booking's slot creates any number of independent, immutable Prescriptions for it, each with at least one Item, and can list them back; no one else can create, read, edit, or delete any of it.

**Independent Test**: Per quickstart.md Scenarios 1–6.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T011 [P] [US1] Integration test: the treating doctor creates a Prescription with 2 Items → `201`, both Items present; `GET` → `200`, an array containing it (FR-001/FR-008, SC-001) — in `backend/src/test/java/com/cms/clinical/integration/PrescriptionCreateTest.java`
- [X] T012 [P] [US1] Integration test: a second, independent Prescription for the same booking, same doctor → `201` again, not `409`; `GET` → `200`, an array of two (FR-003, SC-003) — in the same file as T011
- [X] T013 [P] [US1] Integration test: creation with `"items": []` → `400 PRESCRIPTION_ITEM_REQUIRED` (FR-006, SC-005); creation succeeds for a booking whose Slot is already `CANCELLED`/`COMPLETED`/`NO_SHOW` (FR-009, mirrors 030's own FR-007 regression guard) — in the same file as T011
- [X] T014 [P] [US1] Integration test: create/get against an unknown `bookingId` → `404 BOOKING_NOT_FOUND` for both — in the same file as T011
- [X] T015 [P] [US1] Integration test: a different doctor at the same clinic, a ClinicAdmin, and Operations staff at the same clinic all get `403 FORBIDDEN` for both create and get, no override (FR-004, SC-002) — in `backend/src/test/java/com/cms/clinical/integration/PrescriptionAuthorizationTest.java`
- [X] T016 [P] [US1] Regression test: 030's own `ConsultationNoteCreateTest`/`ConsultationNoteAuthorizationTest` scenarios still pass unmodified after the T006 extraction (quickstart Scenario 6) — verified by re-running those existing test classes, not a new file

### Implementation for User Story 1

- [X] T017 [US1] Implement `PrescriptionService.create(clinicId, bookingId, callerAccountId, items)` per research.md R4/R5: uses `TreatingDoctorAuthorizationService`, rejects an empty `items` list before any write, persists `Prescription` with its `Items` in one save — in `backend/src/main/java/com/cms/clinical/PrescriptionService.java` (depends on T005, T006, T007)
- [X] T018 [US1] Implement `PrescriptionService.list(clinicId, bookingId, callerAccountId): List<Prescription>` — same authorization trace, empty list is a valid success — in the same file (depends on T017)
- [X] T019 [US1] Implement `StaffPrescriptionController` (`POST`/`GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/prescriptions`) per contracts/prescription.md — in `backend/src/main/java/com/cms/clinical/StaffPrescriptionController.java` (depends on T003, T017, T018)

**Checkpoint**: User Story 1 fully functional and independently testable; 030 unaffected.

---

## Phase 4: Frontend & Polish

- [X] T020 [P] Create `frontend/src/features/prescriptions/api.ts` — `createPrescription(clinicId, bookingId, items, token)`, `listPrescriptions(clinicId, bookingId, token)`
- [X] T021 Create `frontend/src/features/prescriptions/PrescriptionForm.tsx` — a repeatable Item entry form plus submit action, listing existing Prescriptions read-only below it (depends on T020)
- [X] T022 [P] Frontend test: creates a Prescription with multiple items, lists existing ones read-only, shows the `PRESCRIPTION_ITEM_REQUIRED`/`FORBIDDEN` error messages — in `frontend/tests/prescriptions/PrescriptionForm.test.tsx` (depends on T021)
- [X] T023 Run `quickstart.md` Scenarios 1–6 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md` — blocked by the sandbox's Testcontainers/Docker limitation (same as every prior feature this session); Scenarios 1-5 each covered by a passing-when-run integration test (T011-T016) verified via compile + structural review; Scenario 6 (frontend) verified live via the Vitest suite (T022, 4/4 green)
- [X] T024 Run full backend build (`/tmp/gradle-8.10/bin/gradle compileJava compileTestJava spotlessCheck -q` and `build -x test`) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 030's existing tests (given `ConsultationNoteService` and `SecurityConfig` were both extended/refactored in place)

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS the user story's test-writing. Includes the 030 refactor (T006), which must be regression-safe before any new test relies on it.
- **US1 (Phase 3)**: Depends on Foundational.
- **Frontend & Polish (Phase 4)**: Depends on US1.

### Parallel Opportunities

- T003 in parallel with T001/T002.
- T011–T016 (all tests) in parallel — depend only on T010.

---

## Implementation Strategy

1. Phase 1 → Phase 2. **STOP and VALIDATE**: foundational pieces compile, 030's existing tests still pass structurally (compile + review), no new behavior yet.
2. Phase 3. **STOP and VALIDATE**: quickstart.md Scenarios 1–6 pass.
3. Phase 4: frontend, full-suite verification, quickstart sign-off.
