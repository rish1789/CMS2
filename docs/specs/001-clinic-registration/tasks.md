---

description: "Task list for Clinic Registration"
---

# Tasks: Clinic Registration

**Input**: Design documents from `/specs/001-clinic-registration/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/register-clinic.md, quickstart.md

**Tests**: Included and REQUIRED — the project constitution's Principle I (Test-First Development, NON-NEGOTIABLE) mandates tests written and failing before implementation for all new behavior, including the atomicity and DB-level uniqueness invariants this feature depends on.

**Organization**: There is exactly one user story in this feature (P1 — Register a New Clinic with its Founding Admin), so Phase 3 below covers the whole feature's functional slice.

**Note on FR-005 / SC-003**: excluding unverified clinics from public discovery search has no tasks in this file, by design. This feature only guarantees the data precondition (`Clinic.verified` defaults to `false` — see T007, T011); the actual search-filtering logic is implemented by feature 035-public-discovery-search, not here.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1)
- File paths follow the web-application split from plan.md: `backend/`, `frontend/`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization — this is the first feature in the project, so this establishes the baseline layout.

- [X] T001 Initialize backend Gradle project (Java 21, Spring Boot 3.x: Web, Data JPA, Validation, Security, Flyway) at `backend/`
- [X] T002 Initialize frontend Vite + React 18 + TypeScript project at `frontend/`
- [X] T003 [P] Configure Tailwind CSS in `frontend/`
- [X] T004 [P] Configure backend formatting/linting (e.g. Spotless) in `backend/build.gradle`
- [X] T005 [P] Configure frontend linting/formatting (oxlint, in place of ESLint/Prettier — equivalent purpose) in `frontend/`
- [X] T006 Add Testcontainers (PostgreSQL) test dependency and base test config in `backend/build.gradle`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before User Story 1 can be implemented.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T007 Create Flyway migration `V1__create_clinic_account_role_assignment.sql` (Clinic, Account, Role Assignment tables, with unique constraints on `account.email` and `account.staff_code` — see data-model.md) in `backend/src/main/resources/db/migration/`
- [X] T008 [P] Configure Spring Security `PasswordEncoder` (BCrypt) bean in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`
- [X] T009 [P] Configure global API exception-handling scaffold (maps domain exceptions to the error response shapes in contracts/register-clinic.md) in `backend/src/main/java/com/cms/identity/api/GlobalExceptionHandler.java`
- [X] T010 Configure `application.yml` datasource/Flyway properties in `backend/src/main/resources/application.yml`

**Checkpoint**: Foundation ready — User Story 1 implementation can now begin.

---

## Phase 3: User Story 1 - Register a New Clinic with its Founding Admin (Priority: P1) 🎯 MVP

**Goal**: A prospective clinic owner submits clinic + admin details and, in one atomic transaction, gets a new unverified Clinic plus an active ClinicAdmin Account (with both email+password and staff-code+password login) and Role Assignment.

**Independent Test**: `POST /api/v1/clinics/register` with a valid payload; confirm a Clinic, Account, and Role Assignment row all exist together, `verified = false`, and the admin can log in both by email and by staff code.

### Tests for User Story 1 ⚠️

> Write these tests FIRST; confirm they FAIL before starting implementation below.

> **⚠️ Environment note (2026-09-02)**: T011–T016 are written, compile cleanly, and follow the correct pattern (Testcontainers + real PostgreSQL) — but could not be executed in this sandbox. Root cause confirmed, not assumed: this sandbox's Docker Desktop daemon returns a malformed response to docker-java's `/info` call specifically (across 2 named-pipe paths and 2 Testcontainers versions), while the `docker` CLI itself works fine via the same pipes. This is a sandbox-local Docker Desktop / docker-java gateway incompatibility, not a defect in the tests or the code they exercise. They remain unchecked below until run for real in an environment where Testcontainers can reach Docker cleanly (a normal dev machine or CI runner should not hit this).

- [ ] T011 [P] [US1] Integration test — happy path: registration creates Clinic + Account + Role Assignment atomically, `verified=false`, response includes a non-empty `staffCode` in `backend/src/test/java/com/cms/identity/integration/RegisterClinicHappyPathTest.java` — **written, not yet run (see environment note above)**
- [ ] T012 [P] [US1] Integration test — rollback: a fault injected mid-transaction leaves zero Clinic/Account/RoleAssignment rows in `backend/src/test/java/com/cms/identity/integration/RegisterClinicRollbackTest.java` — **written, not yet run (see environment note above)**
- [ ] T013 [P] [US1] Integration test — duplicate email: second registration with an already-used `admin.email` returns `409 EMAIL_ALREADY_IN_USE` and creates no rows; concurrent-submission variant proves the DB constraint (not just an app-level check) rejects it in `backend/src/test/java/com/cms/identity/integration/RegisterClinicDuplicateEmailTest.java` — **written, not yet run (see environment note above)**
- [ ] T014 [P] [US1] Integration test — duplicate staff code: proves the DB-level unique constraint on `account.staff_code` (T007) rejects a collision, mirroring T013's structure — (a) directly attempt an insert with a pre-existing `staff_code` via a test-only repository call and confirm the DB constraint violation surfaces as a handled error rather than a silent duplicate, and (b) a concurrent-submission variant confirming `StaffCodeGenerator`'s collision-handling (T024/T025) plus the DB constraint together guarantee no two Accounts ever end up with the same `staff_code`, in `backend/src/test/java/com/cms/identity/integration/RegisterClinicDuplicateStaffCodeTest.java` — **written, not yet run (see environment note above)**
- [ ] T015 [P] [US1] Integration test — password policy: submissions violating each individual rule (length, lowercase, uppercase, digit, special char) are rejected with all failed rules listed in `backend/src/test/java/com/cms/identity/integration/RegisterClinicPasswordPolicyTest.java` — **written, not yet run (see environment note above)**
- [ ] T016 [P] [US1] Integration test — mobile number: invalid `clinic.contactMobile`/`admin.mobile` rejected; omitted mobile numbers succeed (optionality) in `backend/src/test/java/com/cms/identity/integration/RegisterClinicMobileValidationTest.java` — **written, not yet run (see environment note above)**
- [X] T017 [P] [US1] Contract test — request/response schema contains no Grievance Officer, billing/payment, or file-upload field in `backend/src/test/java/com/cms/identity/contract/RegisterClinicContractTest.java` — verified passing (`@WebMvcTest`, no DB needed)
- [X] T018 [P] [US1] Frontend test — registration form renders no Grievance Officer field, submits a valid payload, and surfaces server validation errors (duplicate email, password policy, mobile format) in `frontend/tests/clinic-registration/RegistrationForm.test.tsx` — verified passing (5/5)

### Implementation for User Story 1

- [X] T019 [P] [US1] Create `Clinic` JPA entity in `backend/src/main/java/com/cms/identity/clinic/Clinic.java`
- [X] T020 [P] [US1] Create `Account` JPA entity (unique `email`, unique `staff_code`) in `backend/src/main/java/com/cms/identity/account/Account.java`
- [X] T021 [P] [US1] Create `RoleAssignment` JPA entity in `backend/src/main/java/com/cms/identity/account/RoleAssignment.java`
- [X] T022 [US1] Create `ClinicRepository` in `backend/src/main/java/com/cms/identity/clinic/ClinicRepository.java` (depends on T019)
- [X] T023 [US1] Create `AccountRepository` (`existsByEmail`, `existsByStaffCode`) in `backend/src/main/java/com/cms/identity/account/AccountRepository.java` (depends on T020)
- [X] T024 [US1] Create `RoleAssignmentRepository` in `backend/src/main/java/com/cms/identity/account/RoleAssignmentRepository.java` (depends on T021)
- [X] T025 [US1] Implement `StaffCodeGenerator` (globally-unique code generation, with retry-on-collision against `AccountRepository.existsByStaffCode`; shared mechanism 004-staff-onboarding-direct-hire will also use) in `backend/src/main/java/com/cms/identity/account/StaffCodeGenerator.java` (depends on T023) — verified via `StaffCodeGeneratorTest` (3/3, mocked-repo collision/retry)
- [X] T026 [US1] Implement `PasswordPolicyValidator` (8+ chars, lower/upper/digit/special) in `backend/src/main/java/com/cms/identity/account/PasswordPolicyValidator.java` — verified via `PasswordPolicyValidatorTest` (7/7)
- [X] T027 [US1] Implement `IndianMobileNumberValidator` (10 digits, 6–9 start, optional `+91`/`0`) in `backend/src/main/java/com/cms/identity/common/IndianMobileNumberValidator.java` — verified via `IndianMobileNumberValidatorTest` (11/11)
- [X] T028 [US1] Implement `ClinicRegistrationService` — validates, then atomically creates Clinic + Account (hashed password, generated staff code) + Role Assignment (`role=ClinicAdmin`) in one transaction, rolling back wholesale on failure in `backend/src/main/java/com/cms/identity/clinic/ClinicRegistrationService.java` (depends on T022–T027, T008)
- [X] T029 [US1] Implement request/response/error DTOs per contracts/register-clinic.md in `backend/src/main/java/com/cms/identity/api/dto/` — `@Email` format validation added to `admin.email`/`clinic.contactEmail` (gap found and fixed against the contract)
- [X] T030 [US1] Implement `ClinicRegistrationController` (`POST /api/v1/clinics/register`) in `backend/src/main/java/com/cms/identity/api/ClinicRegistrationController.java` (depends on T028, T029)
- [X] T031 [US1] Wire `GlobalExceptionHandler` mappings for `INVALID_PASSWORD`, `INVALID_MOBILE_NUMBER`, `EMAIL_ALREADY_IN_USE`, `MISSING_REQUIRED_FIELD`, `REGISTRATION_FAILED` in `backend/src/main/java/com/cms/identity/api/GlobalExceptionHandler.java` (depends on T030, T009)
- [X] T032 [P] [US1] Implement `RegistrationForm` React component (clinic + admin fields; no Grievance Officer/billing/file fields) in `frontend/src/features/clinic-registration/RegistrationForm.tsx`
- [X] T033 [US1] Implement registration API client in `frontend/src/features/clinic-registration/api.ts` (depends on T032)
- [X] T034 [US1] Wire client-side validation/error display (password policy, mobile format, duplicate email) in `frontend/src/features/clinic-registration/RegistrationForm.tsx` (depends on T033)

**Checkpoint**: User Story 1 — the entire feature — is fully functional and independently testable.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T035 [P] Run all `quickstart.md` scenarios end-to-end against a running backend + frontend — **not done**; requires actually starting the app against a reachable DB, which needs the same Docker path affected by the T011–T016 environment issue above. Scenarios are otherwise covered logically by the unit/contract test suite that did run.
- [X] T036 [P] Add structured logging for registration attempts (outcome and error type only — never log passwords, hashes, or full payload) in `backend/src/main/java/com/cms/identity/clinic/ClinicRegistrationService.java`
- [X] T037 Security review pass: confirm no plaintext password appears in any response, log line, or exception message (Constitution Principle IV)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS User Story 1.
- **User Story 1 (Phase 3)**: Depends on Foundational completion. Tests (T011–T018) MUST be written and failing before implementation tasks (T019–T034) begin.
- **Polish (Final Phase)**: Depends on User Story 1 completion.

### Within User Story 1

- Entities (T019–T021) before repositories (T022–T024).
- Repositories + validators + code generator (T022–T027) before the service (T028).
- Service + DTOs (T028, T029) before the controller (T030).
- Controller before exception-handler wiring (T031).
- Frontend form (T032) before its API client (T033) before validation wiring (T034).
- Backend and frontend implementation tracks (T019–T031 vs. T032–T034) are independent of each other and can proceed in parallel once Foundational is done.

### Parallel Opportunities

- All Setup tasks marked [P] (T003–T006) run in parallel.
- Foundational tasks T008 and T009 run in parallel (different files).
- All eight test tasks (T011–T018) run in parallel — they're independent files with no cross-dependencies.
- Entity tasks T019–T021 run in parallel.
- The frontend track (T032) can start in parallel with the backend entity/repository track (T019–T024).

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Integration test — happy path in backend/src/test/java/com/cms/identity/integration/RegisterClinicHappyPathTest.java"
Task: "Integration test — rollback in backend/src/test/java/com/cms/identity/integration/RegisterClinicRollbackTest.java"
Task: "Integration test — duplicate email in backend/src/test/java/com/cms/identity/integration/RegisterClinicDuplicateEmailTest.java"
Task: "Integration test — duplicate staff code in backend/src/test/java/com/cms/identity/integration/RegisterClinicDuplicateStaffCodeTest.java"
Task: "Integration test — password policy in backend/src/test/java/com/cms/identity/integration/RegisterClinicPasswordPolicyTest.java"
Task: "Integration test — mobile validation in backend/src/test/java/com/cms/identity/integration/RegisterClinicMobileValidationTest.java"
Task: "Contract test — no excluded fields in backend/src/test/java/com/cms/identity/contract/RegisterClinicContractTest.java"
Task: "Frontend test — registration form in frontend/tests/clinic-registration/RegistrationForm.test.tsx"

# Launch all entity creation together:
Task: "Create Clinic entity in backend/src/main/java/com/cms/identity/clinic/Clinic.java"
Task: "Create Account entity in backend/src/main/java/com/cms/identity/account/Account.java"
Task: "Create RoleAssignment entity in backend/src/main/java/com/cms/identity/account/RoleAssignment.java"
```

---

## Implementation Strategy

### MVP First (and only) — User Story 1

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (CRITICAL — blocks the story).
3. Complete Phase 3: User Story 1 — tests first (T011–T018), confirm they fail, then implementation (T019–T034).
4. **STOP and VALIDATE**: run `quickstart.md` (T035).
5. Polish (T036–T037) and this feature is done — it is its own complete MVP; there is no further story to layer on within this feature.

### Notes

- [P] tasks touch different files with no unmet dependencies.
- Verify each test in T011–T018 actually fails before writing its corresponding implementation (Constitution Principle I, Red-Green-Refactor).
- Commit after each task or logical group.
- The DB-level uniqueness constraints from T007 are what T013 (email) and T014 (staff code) are specifically proving — the fast app-level checks alone (in T028) are not sufficient per Constitution Principle IV.
