---

description: "Task list for Fee Resolution & Locking at Booking Time"
---

# Tasks: Fee Resolution & Locking at Booking Time

**Input**: Design documents from `/specs/017-fee-resolution-locking/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/fee-resolution.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 resolution core, US2 = P2 configuration surface) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

New module: `backend/src/main/java/com/cms/booking/`, `backend/src/test/java/com/cms/booking/integration/`. One extension: `backend/src/main/java/com/cms/identity/account/RoleAssignmentRepository.java`.

---

## Phase 1: Setup

**Purpose**: New module scaffolding — entities, DTOs, exceptions, migration.

- [X] T001 [P] Create exceptions `DoctorProfileNotFoundException`, `AppointmentTypeNotFoundException`, `NoFeeConfiguredException`, `ForbiddenException` in `backend/src/main/java/com/cms/booking/`
- [X] T002 [P] Create `CreateAppointmentTypeRequest`, `AppointmentTypeResponse`, `SetDefaultFeeRequest` DTOs per contracts/fee-resolution.md in `backend/src/main/java/com/cms/booking/dto/`
- [X] T003 Create migration `V9__create_appointment_type_and_default_fee.sql` — `appointment_type` and `doctor_default_fee` tables (`BigDecimal(10,2)` fee columns, unique `doctor_profile_id` on `doctor_default_fee`) per data-model.md — in `backend/src/main/resources/db/migration/V9__create_appointment_type_and_default_fee.sql`
- [X] T004 Create `AppointmentType` and `DoctorDefaultFee` entities per data-model.md in `backend/src/main/java/com/cms/booking/AppointmentType.java` and `DoctorDefaultFee.java` (depends on T003)

**Checkpoint**: Schema and types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Repositories, the new security chain, and shared test fixture both stories build on.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T005 [P] Create `AppointmentTypeRepository` (`findByDoctorProfile_Id`) and `DoctorDefaultFeeRepository` (`findByDoctorProfile_Id`) in `backend/src/main/java/com/cms/booking/` (depends on T004)
- [X] T006 Add `findByAccount_IdAndRoleAndActiveTrue(UUID accountId, RoleAssignment.Role role)` to `RoleAssignmentRepository` in `backend/src/main/java/com/cms/identity/account/RoleAssignmentRepository.java`
- [X] T007 Create `BookingSecurityConfig` — `@Order(6)`, `securityMatcher("/api/v1/doctors/**")`, reuses `StaffJwtAuthenticationFilter`/`StaffJwtService`/`StaffAuthenticationEntryPoint`, `anyRequest().authenticated()` — in `backend/src/main/java/com/cms/booking/BookingSecurityConfig.java` (research.md)
- [X] T008 Create `AbstractBookingIntegrationTest.java` — Testcontainers + MockMvc base class with a `saveDoctorProfile()` builder, a `doctorToken()`/`clinicAdminTokenForDoctorsClinic()`/`unrelatedStaffToken()` set of helpers (mirroring `AbstractScheduleIntegrationTest`'s pattern) — in `backend/src/test/java/com/cms/booking/integration/AbstractBookingIntegrationTest.java` (depends on T005, T006, T007)

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: Fee Resolves in Strict Order, With a Hard Block When Nothing Is Configured (Priority: P1) 🎯 MVP

**Goal**: `FeeResolutionService.resolve(doctorProfileId, appointmentTypeId)` implements the exact override → default → block order and the wrong-doctor rejection.

**Independent Test**: Per quickstart.md Scenarios 1–4 — seed `AppointmentType`/`DoctorDefaultFee` combinations directly and call `resolve()`.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T009 [P] [US1] Integration test: an Appointment Type with an override resolves to the override regardless of a default fee also present, in `backend/src/test/java/com/cms/booking/integration/FeeResolutionOrderTest.java`
- [X] T010 [P] [US1] Integration test (same file): an Appointment Type with no override resolves to the doctor's default fee when one exists, in `backend/src/test/java/com/cms/booking/integration/FeeResolutionOrderTest.java`
- [X] T011 [P] [US1] Integration test: an Appointment Type with no override and no default fee throws `NoFeeConfiguredException` — no fee is ever returned, in `backend/src/test/java/com/cms/booking/integration/FeeResolutionHardBlockTest.java`
- [X] T012 [P] [US1] Integration test: resolving an Appointment Type against a different doctor than it belongs to throws `AppointmentTypeNotFoundException`, in `backend/src/test/java/com/cms/booking/integration/FeeResolutionWrongDoctorTest.java`

### Implementation for User Story 1

- [X] T013 [US1] Implement `FeeResolutionService.resolve(UUID doctorProfileId, UUID appointmentTypeId)` per data-model.md/contracts in `backend/src/main/java/com/cms/booking/FeeResolutionService.java` (depends on T005)

**Checkpoint**: User Story 1 fully functional and independently testable — the resolution algorithm is correct against directly-seeded data.

---

## Phase 4: A Doctor's Fee Configuration Is Managed Explicitly (Priority: P2)

**Goal**: `BookingController`'s three endpoints let the Doctor (or an authorized ClinicAdmin) create/list Appointment Types and set the default fee, with correct authorization.

**Independent Test**: Per quickstart.md Scenario 5.

### Tests for User Story 2 (write first, confirm they FAIL before implementation)

- [X] T014 [P] [US2] Integration test: the Doctor's own token and a ClinicAdmin's token (at a clinic the doctor is actively staffed at) both succeed on create/list/set-default-fee; an unrelated staff member's token is `403` on all three, in `backend/src/test/java/com/cms/booking/integration/AppointmentTypeConfigAuthorizationTest.java`
- [X] T015 [P] [US2] Integration test: setting the default fee twice replaces (upserts) the prior value, not duplicates it, in `backend/src/test/java/com/cms/booking/integration/SetDefaultFeeTest.java`

### Implementation for User Story 2

- [X] T016 [US2] Implement `AppointmentTypeService` (`create`, `list`, `setDefaultFee`, shared `requireAuthorized`) per data-model.md in `backend/src/main/java/com/cms/booking/AppointmentTypeService.java` (depends on T006, T013)
- [X] T017 [US2] Implement `BookingController` (`POST`/`GET .../appointment-types`, `PUT .../default-fee`) per contracts/fee-resolution.md in `backend/src/main/java/com/cms/booking/BookingController.java` (depends on T016, T007)
- [X] T018 [US2] Implement `BookingExceptionHandler` mapping T001's exceptions to contracts/fee-resolution.md's error shapes (reusing `com.cms.identity.api.dto.ErrorResponse`) in `backend/src/main/java/com/cms/booking/BookingExceptionHandler.java` (depends on T001)

**Checkpoint**: Both user stories independently functional — resolution is correct, and the configuration surface that feeds it works and is properly authorized.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T019 Run `quickstart.md` Scenarios 1–5 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T020 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected); confirm green with zero regressions in prior features' tests

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational.
- **User Story 2 (Phase 4)**: Depends on US1's `FeeResolutionService` existing (`AppointmentTypeService`/`BookingController` are additive on top of it).
- **Polish (Phase 5)**: Depends on both stories.

### Parallel Opportunities

- T001, T002 in parallel (different files).
- T005 (both repositories) in parallel with T006/T007 (different files).
- T009–T012 (all US1 tests) in parallel — depend only on T008.
- T014, T015 in parallel — depend on US1's `FeeResolutionService`.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenarios 1–4 pass by calling `FeeResolutionService.resolve()` directly against seeded data.

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenario 5 passes — the configuration surface exists and is authorized correctly.
3. Phase 5: full-suite verification and quickstart sign-off.
