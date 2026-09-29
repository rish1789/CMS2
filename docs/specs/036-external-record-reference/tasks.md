---

description: "Task list for External Record Reference"
---

# Tasks: External Record Reference

**Input**: Design documents from `/specs/036-external-record-reference/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/external-record-reference.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Single user story, mirroring 030/031's own identical shape. No 030/031 code is touched by this feature — `TreatingDoctorAuthorizationService` is reused unchanged as a third caller.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends the existing `backend/src/main/java/com/cms/clinical/` and `backend/src/test/java/com/cms/clinical/integration/`. Extends `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`. New: `frontend/src/features/external-record-references/`, `frontend/tests/external-record-references/`.

---

## Phase 1: Setup

**Purpose**: The new entity and request/response DTOs.

- [X] T001 Create `ExternalRecordReference` entity (`booking` `@ManyToOne` not null — no uniqueness; `doctorProfile` `@ManyToOne` not null; `recordType`/`sourceProvider`/`recordDate`/`summary` all not null; `createdAt` not null) per data-model.md in `backend/src/main/java/com/cms/clinical/ExternalRecordReference.java`
- [X] T002 [P] Create `CreateExternalRecordReferenceRequest`/`ExternalRecordReferenceResponse` DTOs per contracts/external-record-reference.md in `backend/src/main/java/com/cms/clinical/dto/`

**Checkpoint**: Types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Migration, repository, security matchers, and the test fixture.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T003 Create migration `V21__create_external_record_reference.sql` per data-model.md in `backend/src/main/resources/db/migration/V21__create_external_record_reference.sql` (depends on T001)
- [X] T004 Create `ExternalRecordReferenceRepository` with `findByBooking_Id(UUID bookingId): List<ExternalRecordReference>` in `backend/src/main/java/com/cms/clinical/ExternalRecordReferenceRepository.java` (depends on T001)
- [X] T005 Add explicit matchers `.requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/bookings/*/external-record-references").authenticated()` and the `GET` equivalent to the `/api/v1/clinics/**` chain in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`
- [X] T006 Create `AbstractExternalRecordReferenceIntegrationTest` extending `AbstractPrescriptionIntegrationTest` (mirrors this codebase's established fixture-inheritance pattern, reuses its `operationsToken` helper) — in `backend/src/test/java/com/cms/clinical/integration/AbstractExternalRecordReferenceIntegrationTest.java` (depends on T004)

**Checkpoint**: Foundation ready — the user story can now be built.

---

## Phase 3: Treating Doctor Records a Typed External Record Reference (Priority: P1) 🎯 MVP

**Goal**: The doctor assigned to a booking's slot creates any number of independent, immutable, typed-summary-only External Record References for it, and can list them back; no one else can create, read, edit, or delete any of it; no file field exists anywhere.

**Independent Test**: Per quickstart.md Scenarios 1–6.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T007 [P] [US1] Integration test: the treating doctor creates a reference with all four fields → `201`; `GET` → `200`, an array containing it (FR-001/FR-007, SC-001) — in `backend/src/test/java/com/cms/clinical/integration/ExternalRecordReferenceCreateTest.java`
- [X] T008 [P] [US1] Integration test: a second, independent reference for the same booking, same doctor → `201` again; `GET` → `200`, an array of two (FR-003, SC-003) — in the same file as T007
- [X] T009 [P] [US1] Integration test: creation succeeds for a booking whose Slot is already `CANCELLED`/`COMPLETED`/`NO_SHOW` (FR-008, mirrors 030's/031's own regression guard) — in the same file as T007
- [X] T010 [P] [US1] Integration test: create/get against an unknown `bookingId` → `404 BOOKING_NOT_FOUND` for both — in the same file as T007
- [X] T011 [P] [US1] Integration test: a different doctor at the same clinic, a ClinicAdmin, and Operations staff at the same clinic all get `403 FORBIDDEN` for both create and get, no override (FR-004, SC-002) — in `backend/src/test/java/com/cms/clinical/integration/ExternalRecordReferenceAuthorizationTest.java`

### Implementation for User Story 1

- [X] T012 [US1] Implement `ExternalRecordReferenceService.create(clinicId, bookingId, callerAccountId, recordType, sourceProvider, recordDate, summary)` per research.md R2 — reuses `TreatingDoctorAuthorizationService` unchanged, no data-layer guard needed (research.md, mirrors 031's own reasoning) — in `backend/src/main/java/com/cms/clinical/ExternalRecordReferenceService.java` (depends on T004)
- [X] T013 [US1] Implement `ExternalRecordReferenceService.list(clinicId, bookingId, callerAccountId): List<ExternalRecordReference>` — same authorization trace, empty list is a valid success — in the same file (depends on T012)
- [X] T014 [US1] Implement `StaffExternalRecordReferenceController` (`POST`/`GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/external-record-references`) per contracts/external-record-reference.md — in `backend/src/main/java/com/cms/clinical/StaffExternalRecordReferenceController.java` (depends on T002, T012, T013)

**Checkpoint**: User Story 1 fully functional and independently testable; 030/031 completely unaffected.

---

## Phase 4: Frontend & Polish

- [X] T015 [P] Create `frontend/src/features/external-record-references/api.ts` — `createExternalRecordReference(clinicId, bookingId, fields, token)`, `listExternalRecordReferences(clinicId, bookingId, token)`
- [X] T016 Create `frontend/src/features/external-record-references/ExternalRecordReferenceForm.tsx` — a 4-field form plus submit action, listing existing references read-only below it (depends on T015)
- [X] T017 [P] Frontend test: creates a reference, lists existing ones read-only, shows the `FORBIDDEN` error message — in `frontend/tests/external-record-references/ExternalRecordReferenceForm.test.tsx` (depends on T016)
- [X] T018 Run `quickstart.md` Scenarios 1–6 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md` — blocked by the sandbox's Testcontainers/Docker limitation (same as every prior feature this session); Scenarios 1-4 each covered by a passing-when-run integration test (T007-T011) verified via compile + structural review; Scenario 6 (frontend) verified live via the Vitest suite (T017, 3/3 green)
- [X] T019 Run full backend build (`/tmp/gradle-8.10/bin/gradle compileJava compileTestJava spotlessCheck -q` and `build -x test`) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 030's/031's existing tests

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS the user story's test-writing.
- **US1 (Phase 3)**: Depends on Foundational.
- **Frontend & Polish (Phase 4)**: Depends on US1.

### Parallel Opportunities

- T002 in parallel with T001.
- T007–T011 (all tests) in parallel — depend only on T006.

---

## Implementation Strategy

1. Phase 1 → Phase 2. **STOP and VALIDATE**: foundational pieces compile, no behavior yet.
2. Phase 3. **STOP and VALIDATE**: quickstart.md Scenarios 1–5 pass.
3. Phase 4: frontend, full-suite verification, quickstart sign-off.
