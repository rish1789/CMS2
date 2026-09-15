---

description: "Task list for Staff Onboarding (Direct-Hire)"
---

# Tasks: Staff Onboarding (Direct-Hire)

**Input**: Design documents from `/specs/004-staff-onboarding-direct-hire/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/staff-onboarding.md, quickstart.md

**Tests**: Included and REQUIRED per Constitution Principle I.

**Organization**: Two user stories — US1 (P1, staff login + Operations onboarding) and US2 (P2, Doctor onboarding extension). Reuses 001's `Account`/`RoleAssignment`/`PasswordEncoder`/`PasswordPolicyValidator`/`StaffCodeGenerator` and `com.cms.common.IndianMobileNumberValidator` (002) directly — no duplication.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

- [X] T001 [P] Create `frontend/src/features/staff-login/` and `frontend/src/features/staff-onboarding/` directory scaffolds

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 Create Flyway migration `V3__create_doctor_profile.sql` (`doctor_profile` table: `id`, `account_id` FK, `specialization`, `license_number`, `experience_years`, `license_verified` default `false`, `created_at`; one row per Account — see data-model.md) in `backend/src/main/resources/db/migration/`
- [X] T003 [P] Implement `StaffJwtService` — issues/validates `STAFF`-audience tokens, structurally distinct from 002's `PATIENT`-audience tokens (research.md) in `backend/src/main/java/com/cms/identity/account/StaffJwtService.java`
- [X] T004 [P] Implement staff-facing security (`@Order(4)`) — permits `POST /api/v1/staff/login`, JWT-protects `/api/v1/clinics/*/staff/**` — **deviation**: extended 001's existing `SecurityConfig.java` (a second `@Bean` chain) rather than a separate `StaffSecurityConfig.java` file, per that file's own prior code comment anticipating exactly this
- [X] T005 [P] Configure exception-handling scaffold for staff/onboarding error shapes in `backend/src/main/java/com/cms/identity/staff/StaffExceptionHandler.java`
- [X] T006 [P] Implement `TemporaryPasswordGenerator` — generates a random password guaranteed to satisfy `PasswordPolicyValidator` by construction (FR-006, SC-003; neither `PasswordPolicyValidator` (validates) nor `PasswordEncoder` (hashes) generates one) in `backend/src/main/java/com/cms/identity/account/TemporaryPasswordGenerator.java`

> **Bugs found and fixed during this phase** (both touched 001/002's already-converged code, verified no regression): (1) the JWT filter was initially a `@Component`, which Spring Boot auto-registers into every `@WebMvcTest` slice application-wide — broke 001's and 002's previously-passing contract tests. Fixed by constructing it with `new` inside `SecurityConfig`'s filter-chain bean instead of exposing it as a bean. (2) Extending `SecurityConfig`'s constructor (new JWT/entry-point params) broke 001's `RegisterClinicContractTest`, which `@Import`s that class — fixed by adding the new dependencies to that test's imports and a test property. Both re-verified passing after the fixes.

**Checkpoint**: Foundation ready.

---

## Phase 3: User Story 1 - ClinicAdmin Onboards an Operations Staff Member (Priority: P1) 🎯 MVP

**Goal**: A ClinicAdmin logs in and onboards a new Operations hire in one step, getting back a staff code and temporary password.

**Independent Test**: Log in as a 001-registered ClinicAdmin; `POST` an Operations onboarding request; confirm Account + RoleAssignment exist, active, with `doctorProfileId: null`.

### Tests for User Story 1 ⚠️

> Write these tests FIRST; confirm they FAIL before starting implementation below.

> **⚠️ Environment note (2026-09-02)**: same known sandbox issue as 001/002/003 — Testcontainers cannot reach this sandbox's Docker Desktop daemon. T007–T012 are written, correct by code review (each failing class's stack trace individually confirmed as the same `IllegalStateException`), but unexecuted.

- [ ] T007 [P] [US1] Integration test — staff login: correct credentials succeed with a `STAFF` token; wrong password and unknown email both return the identically-shaped `401` in `backend/src/test/java/com/cms/identity/account/integration/StaffLoginTest.java` — **written, not yet run (see environment note above)**
- [ ] T008 [P] [US1] Integration test — Operations onboarding happy path: `201`, Account + RoleAssignment(`role=Operations`) created and active, `staffCode`/`temporaryPassword` returned, `doctorProfileId: null` in `backend/src/test/java/com/cms/identity/staff/integration/OnboardOperationsHappyPathTest.java` — **written, not yet run (see environment note above)**
- [ ] T009 [P] [US1] Integration test — `role=ClinicAdmin` and `role=SuperAdmin` both rejected `400 INVALID_ROLE` server-side, no rows created, even bypassing client validation (FR-003) in `backend/src/test/java/com/cms/identity/staff/integration/OnboardingRoleRestrictionTest.java` — **written, not yet run (see environment note above)**
- [ ] T010 [P] [US1] Integration test — no token → `401`; valid Doctor/Operations (non-ClinicAdmin) token → `403` (FR-002) in `backend/src/test/java/com/cms/identity/staff/integration/OnboardingAuthorizationTest.java` — **written, not yet run (see environment note above)**
- [ ] T011 [P] [US1] Integration test — duplicate email (already used by any staff Account, per 001's global uniqueness) rejected `409 EMAIL_ALREADY_IN_USE`, no rows created; concurrent-submission variant proves the DB constraint (not just the app-level pre-check) rejects it — same pattern as 001/002's own duplicate-email tests in `backend/src/test/java/com/cms/identity/staff/integration/OnboardingDuplicateEmailTest.java` — **written, not yet run (see environment note above)**
- [ ] T012 [P] [US1] Integration test — invalid-format mobile rejected `400`; omitted mobile succeeds (FR-008) in `backend/src/test/java/com/cms/identity/staff/integration/OnboardingMobileValidationTest.java` — **written, not yet run (see environment note above)**
- [X] T013 [P] [US1] Unit test — `TemporaryPasswordGenerator` output always satisfies `PasswordPolicyValidator` across many generated samples (FR-006, SC-003) in `backend/src/test/java/com/cms/identity/account/TemporaryPasswordGeneratorTest.java` — verified passing (2/2)
- [X] T014 [P] [US1] Contract test — onboarding request/response schema contains no invitation/accept-link field (FR-010), and onboarding never invokes a notification-send call (FR-005) — assert via a mocked/spied notification port that it's never called in `backend/src/test/java/com/cms/identity/staff/contract/StaffOnboardingContractTest.java` — verified passing
- [X] T015 [P] [US1] Frontend test — `StaffLoginForm` and `OnboardStaffForm` (Operations path): login, submit, display returned credentials, surface server errors in `frontend/tests/staff-onboarding/StaffOnboarding.test.tsx` — verified passing (7/7 new, 27/27 total)

### Implementation for User Story 1

- [X] T016 [US1] Implement `StaffAuthController` (`POST /api/v1/staff/login`) reusing 001's `Account`/`PasswordEncoder` in `backend/src/main/java/com/cms/identity/account/StaffAuthController.java` (depends on T003)
- [X] T017 [US1] Implement `DoctorProfile` entity + `DoctorProfileRepository` in `backend/src/main/java/com/cms/identity/doctor/` (depends on T002)
- [X] T018 [US1] Implement `StaffOnboardingService` — validates, generates staff code (reuses `StaffCodeGenerator`) + temp password (`TemporaryPasswordGenerator`, T006), atomically creates Account + RoleAssignment (Operations path only in this story; Doctor branch added in US2) in `backend/src/main/java/com/cms/identity/staff/StaffOnboardingService.java` (depends on T017, T006) — the per-clinic ClinicAdmin-authorization (403) check lives here rather than in security config, since it needs the `clinicId` path variable
- [X] T019 [US1] Implement request/response/error DTOs per contracts/staff-onboarding.md in `backend/src/main/java/com/cms/identity/staff/dto/`
- [X] T020 [US1] Implement `StaffOnboardingController` (`POST /api/v1/clinics/{clinicId}/staff`) — verifies the caller's `STAFF` token belongs to an active ClinicAdmin RoleAssignment for `{clinicId}` (403 otherwise) in `backend/src/main/java/com/cms/identity/staff/StaffOnboardingController.java` (depends on T018, T019, T004)
- [X] T021 [US1] Wire `StaffExceptionHandler` mappings for `UNAUTHORIZED`, `FORBIDDEN`, `INVALID_ROLE`, `MISSING_REQUIRED_FIELD`, `INVALID_MOBILE_NUMBER`, `EMAIL_ALREADY_IN_USE`, `ONBOARDING_FAILED` (depends on T020, T005)
- [X] T022 [P] [US1] Implement `StaffLoginForm` React component in `frontend/src/features/staff-login/StaffLoginForm.tsx`
- [X] T023 [P] [US1] Implement `OnboardStaffForm` React component (name/email/mobile/role fields; Doctor-specific fields added in US2) in `frontend/src/features/staff-onboarding/OnboardStaffForm.tsx`
- [X] T024 [US1] Implement `staff-login`/`staff-onboarding` API clients in their respective `api.ts` (depends on T022, T023)
- [X] T025 [US1] Wire client-side validation/error display and a one-time credential-display screen (staff code + temp password, shown once) (depends on T024)

**Checkpoint**: User Story 1 is fully functional and independently testable.

---

## Phase 4: User Story 2 - ClinicAdmin Onboards a Doctor (Priority: P2)

**Goal**: Extend onboarding to the Doctor path, creating a Doctor Profile in the same transaction.

**Independent Test**: Submit a Doctor onboarding request; confirm Account + RoleAssignment(`role=Doctor`) + DoctorProfile(`license_verified=false`) all exist together.

### Tests for User Story 2 ⚠️

- [ ] T026 [P] [US2] Integration test — Doctor onboarding happy path: `201`, non-null `doctorProfileId`, DB row has `license_verified=false` (FR-007, SC-004) in `backend/src/test/java/com/cms/identity/staff/integration/OnboardDoctorHappyPathTest.java` — **written, not yet run (see environment note above)**
- [ ] T027 [P] [US2] Integration test — `role=Doctor` with missing specialization/license/experience rejected `400 MISSING_REQUIRED_FIELD` in `backend/src/test/java/com/cms/identity/staff/integration/OnboardDoctorMissingFieldsTest.java` — **written, not yet run (see environment note above)**
- [ ] T028 [P] [US2] Integration test — atomicity: a fault injected between Account creation and Doctor Profile creation leaves zero rows across Account/RoleAssignment/DoctorProfile (FR-009, SC-005) in `backend/src/test/java/com/cms/identity/staff/integration/OnboardingAtomicityTest.java` — **written, not yet run (see environment note above)**

### Implementation for User Story 2

- [X] T029 [US2] Extend `StaffOnboardingService` with the Doctor branch — creates `DoctorProfile` in the same transaction as Account/RoleAssignment (depends on T018, T017)
- [X] T030 [US2] Extend the request DTO with the optional `doctor` sub-object and its required-when-role-is-Doctor validation (depends on T019)
- [X] T031 [P] [US2] Add Doctor-specific fields (specialization/license/experience) to `OnboardStaffForm`, shown conditionally when `role=Doctor` (depends on T023)

**Checkpoint**: Both user stories functional together.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T032 [P] Run all `quickstart.md` scenarios end-to-end against a running backend + frontend — **not done**; same Docker root cause as T007–T012/T026–T028 above
- [X] T033 [P] Add structured logging for login/onboarding attempts (outcome and error type only — never log passwords, hashes, or tokens) in `StaffOnboardingService.java` and `StaffAuthController.java`
- [X] T034 Security review pass: confirm no plaintext password, staff-code-as-secret confusion, or JWT ever appears in a log line or exception message (Constitution Principle IV)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational. Tests (T007–T015) before implementation (T016–T025).
- **User Story 2 (Phase 4)**: Depends on Foundational AND on US1's `StaffOnboardingService`/Controller/DTOs existing (T018, T019) — extends the same code paths.
- **Polish (Final Phase)**: Depends on both user stories.

### Parallel Opportunities

- T003, T004, T005, T006 (Foundational) run in parallel.
- All nine US1 test tasks (T007–T015) run in parallel.
- Backend (T016–T021) and frontend (T022–T025) implementation tracks are independent and can proceed in parallel once Foundational is done.
- All three US2 test tasks (T026–T028) run in parallel.

---

## Implementation Strategy

### MVP First — User Story 1

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational.
3. Complete Phase 3: User Story 1 — tests first (T007–T015), then implementation (T016–T025).
4. **STOP and VALIDATE** US1 independently (login + Operations onboarding work; Doctor path not yet supported).
5. Layer in User Story 2 (T026–T031) — Doctor onboarding.
6. Polish (T032–T034).

### Notes

- Verify each test in T007–T015 and T026–T028 actually fails before writing its corresponding implementation (Constitution Principle I).
- T028 is the test proving FR-009/SC-005's atomicity across three tables — the widest-scope invariant in this feature; don't shortcut it to a two-table check.
- The DB-level unique constraint from 001 (`uq_account_email`) is what T011 is proving here, exactly as it did for 001 and 002's own duplicate-email tests — the app-level pre-check alone is not sufficient per Constitution Principle IV.
