---

description: "Task list for Patient Account & Global Login"
---

# Tasks: Patient Account & Global Login

**Input**: Design documents from `/specs/002-patient-account-login/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/patient-account.md, quickstart.md

**Tests**: Included and REQUIRED per Constitution Principle I (Test-First Development, NON-NEGOTIABLE).

**Organization**: Two user stories — US1 (P1, core signup/login) and US2 (P2, optional mobile number). `backend/` and `frontend/` already exist from 001-clinic-registration; this feature adds sibling packages/folders, not new projects.

**Note on FR-010**: viewing a Patient Account holder's "my visit history" (clinic-scoped Patient records actually linked to their account) has no tasks in this file, by design. This feature only creates the Patient Account identity itself; the linking logic is 019-patient-record-auto-creation-phone-linking, and an actual history-viewing UI/endpoint belongs to a booking-history feature not yet in this backlog slice.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

- [X] T001 Add a JWT library dependency (e.g. `io.jsonwebtoken:jjwt-api`/`jjwt-impl`/`jjwt-jackson`) to `backend/build.gradle`
- [X] T002 [P] Create the `frontend/src/features/patient-account/` directory scaffold

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 Create Flyway migration `V2__create_patient_account.sql` (new `patient_account` table: `id`, `email` (unique constraint), `password_hash`, `mobile`, `notification_opt_in` (boolean, default `true` — FR-009), `active` (default `true`), `created_at`; independent of 001's `account` table — see data-model.md) in `backend/src/main/resources/db/migration/`
- [X] T004 [P] Implement `JwtService` — issues and validates tokens carrying a patient-only audience/scope claim (research.md's session-mechanism decision; implements FR-008 structurally) in `backend/src/main/java/com/cms/patient/account/JwtService.java`
- [X] T005 [P] Configure a patient-facing Spring Security filter chain (permits `/api/v1/patients/signup` and `/api/v1/patients/login`; entirely separate chain/config from 001's `SecurityConfig`) in `backend/src/main/java/com/cms/patient/account/SecurityConfig.java` — required scoping 001's chain down to `/api/v1/clinics/**` (`@Order(1)`) alongside this one at `/api/v1/patients/**` (`@Order(2)`) so the two coexist without one shadowing the other
- [X] T006 [P] Configure exception-handling scaffold for patient-facing error shapes (maps to contracts/patient-account.md) in `backend/src/main/java/com/cms/patient/api/PatientExceptionHandler.java`

**Checkpoint**: Foundation ready.

> **Bug found and fixed during this phase**: `@SpringBootApplication`'s implicit component scan only covers its own package and below. The application class lived at `com.cms.identity.IdentityApplication`, so it never scanned the new sibling packages `com.cms.patient`/`com.cms.common` — meaning none of this feature's beans would have been registered at runtime, not just a test artifact. Relocated to `com.cms.CmsApplication` (parent of every module). Verified as the actual root cause: `@WebMvcTest`-based tests failed with "Unable to find a @SpringBootConfiguration" before the fix, passed cleanly after.

---

## Phase 3: User Story 1 - Self-Service Patient Signup and Login (Priority: P1) 🎯 MVP

**Goal**: A visitor signs up with email + password and can immediately log in, receiving a token that only ever grants patient-level access.

**Independent Test**: `POST /api/v1/patients/signup` then `POST /api/v1/patients/login` with the same credentials; confirm a token is returned and its scope is patient-only.

### Tests for User Story 1 ⚠️

> Write these tests FIRST; confirm they FAIL before starting implementation below.

> **⚠️ Environment note (2026-09-02)**: same known sandbox issue as 001-clinic-registration — Testcontainers cannot reach this sandbox's Docker Desktop daemon (confirmed again on this feature: `docker ps` works from the shell, but `IllegalStateException: Previous attempts to find a Docker environment failed` from every Testcontainers-based test, including 001's previously-passing ones — re-run and reconfirmed, not a regression specific to this feature). T007–T011 and T025 are written, compile, and follow the correct pattern, but unexecuted. Non-Testcontainers tests (T012, T013, T014) ran and passed.

- [ ] T007 [P] [US1] Integration test — signup happy path: `201`, exactly one `patient_account` row, `active=true` in `backend/src/test/java/com/cms/patient/integration/PatientSignupHappyPathTest.java` — **written, not yet run (see environment note above)**
- [ ] T008 [P] [US1] Integration test — login: correct credentials succeed with a token; wrong password and unknown email both return the identically-shaped `401 INVALID_CREDENTIALS` (FR-007, no information leak) in `backend/src/test/java/com/cms/patient/integration/PatientLoginTest.java` — **written, not yet run (see environment note above)**
- [ ] T009 [P] [US1] Integration test — duplicate email: second signup with an already-used email returns `409 EMAIL_ALREADY_IN_USE`, no new row; concurrent-submission variant proves the DB constraint (not just the app-level check) rejects it in `backend/src/test/java/com/cms/patient/integration/PatientSignupDuplicateEmailTest.java` — **written, not yet run (see environment note above)**
- [ ] T010 [P] [US1] Integration test — cross-system email independence: the same email used for a 001-style staff Account signup succeeds independently of a Patient Account signup with that same email, and vice versa (FR-004) in `backend/src/test/java/com/cms/patient/integration/PatientAccountCrossSystemEmailTest.java` — **written, not yet run (see environment note above)**
- [ ] T011 [P] [US1] Integration test — password policy: each individual rule violation rejected with all failed rules listed (FR-002) in `backend/src/test/java/com/cms/patient/integration/PatientSignupPasswordPolicyTest.java` — **written, not yet run (see environment note above)**
- [X] T012 [P] [US1] Unit test — a token issued by `JwtService` carries a patient-only audience/scope claim, and a stub staff-only authorization check structurally rejects it (FR-008) in `backend/src/test/java/com/cms/patient/account/JwtServiceScopeTest.java` — verified passing (2/2)
- [X] T013 [P] [US1] Frontend test — `SignupForm`/`LoginForm` render, submit valid payloads, and surface server errors (duplicate email, password policy, invalid credentials) in `frontend/tests/patient-account/PatientAccountForms.test.tsx` — verified passing (9/9, plus 5 pre-existing 001 tests unaffected: 14/14 total)
- [X] T014 [P] [US1] Contract test — signup/login request and response schemas contain no social-login/SSO field (`provider`, `oauthToken`, etc. — FR-011), no `role`/`clinicId`/staff-identity field (FR-008) in `backend/src/test/java/com/cms/patient/contract/PatientAccountContractTest.java` — verified passing (3/3)

### Implementation for User Story 1

- [X] T015 [P] [US1] Create `PatientAccount` JPA entity — including `notificationOptIn` (default `true`, FR-009) — in `backend/src/main/java/com/cms/patient/account/PatientAccount.java`
- [X] T016 [US1] Create `PatientAccountRepository` (`existsByEmail`, `findByEmail`) in `backend/src/main/java/com/cms/patient/account/PatientAccountRepository.java` (depends on T015)
- [X] T017 [US1] Implement `PatientAccountService` — `signup()` (validate, hash password, save) and `authenticate()` (verify password, issue JWT via `JwtService`) in `backend/src/main/java/com/cms/patient/account/PatientAccountService.java` (depends on T016, T004) — reuses 001's `PasswordEncoder` bean via type-based injection (not a second bean declaration, which would have conflicted). `PasswordPolicyValidator` is intentionally a separate copy in `com.cms.patient.account`, not shared with 001's — see judgment-call note below.
- [X] T018 [US1] Implement request/response/error DTOs per contracts/patient-account.md in `backend/src/main/java/com/cms/patient/api/dto/`
- [X] T019 [US1] Implement `PatientAccountController` (`POST /api/v1/patients/signup`, `POST /api/v1/patients/login`) in `backend/src/main/java/com/cms/patient/api/PatientAccountController.java` (depends on T017, T018)
- [X] T020 [US1] Wire `PatientExceptionHandler` mappings for `INVALID_PASSWORD`, `EMAIL_ALREADY_IN_USE`, `INVALID_CREDENTIALS`, `MISSING_REQUIRED_FIELD`, `SIGNUP_FAILED` (depends on T019, T006)
- [X] T021 [P] [US1] Implement `SignupForm` React component (email + password only at this point; mobile added in US2) in `frontend/src/features/patient-account/SignupForm.tsx`
- [X] T022 [P] [US1] Implement `LoginForm` React component in `frontend/src/features/patient-account/LoginForm.tsx`
- [X] T023 [US1] Implement patient-account API client in `frontend/src/features/patient-account/api.ts` (depends on T021, T022) — reuses 001's `VITE_API_BASE_URL` + typed-error-union pattern
- [X] T024 [US1] Wire client-side validation/error display for both forms (depends on T023)

**Checkpoint**: User Story 1 is fully functional and independently testable.

---

## Phase 4: User Story 2 - Optional Mobile Number at Signup (Priority: P2)

**Goal**: A patient can optionally add a mobile number at signup; invalid formats are rejected, omission is fine.

**Independent Test**: Submit signup with a valid mobile, with an invalid one, and with none — confirm success/rejection/success respectively.

### Tests for User Story 2 ⚠️

- [ ] T025 [P] [US2] Integration test — valid Indian-format mobile stored; invalid format rejected with `INVALID_MOBILE_NUMBER`; omitted mobile still succeeds (FR-006) in `backend/src/test/java/com/cms/patient/integration/PatientSignupMobileValidationTest.java` — **written, not yet run (see environment note above)**

### Implementation for User Story 2

- [X] T026 [US2] Relocate `IndianMobileNumberValidator` from `com.cms.identity.common` (001) to a neutral shared package, `backend/src/main/java/com/cms/common/IndianMobileNumberValidator.java`, and update 001's `ClinicRegistrationService` import accordingly — keeps `com.cms.patient` from depending on `com.cms.identity` for an unrelated utility, per this feature's module-boundary decision (research.md) — **verified**: 001's non-Docker tests (`RegisterClinicContractTest`, `StaffCodeGeneratorTest`, `PasswordPolicyValidatorTest`) still pass after the move, confirming no regression
- [X] T027 [US2] Wire the relocated validator into `PatientAccountService.signup()` for the optional `mobile` field (depends on T026, T017)
- [X] T028 [P] [US2] Add the optional mobile field (with format hint/error display) to `SignupForm.tsx` (depends on T021)

**Checkpoint**: Both user stories functional together.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T029 [P] Run all `quickstart.md` scenarios end-to-end against a running backend + frontend — **not done**; same Docker root cause as T007–T011/T025 above blocks starting the app against a reachable DB
- [X] T030 [P] Add structured logging for signup/login attempts (outcome and error type only — never log passwords, hashes, or issued tokens) in `PatientAccountService.java`
- [X] T031 Security review pass: confirm no plaintext password, password hash, or JWT ever appears in a log line or exception message (Constitution Principle IV)

---

## Judgment Call: PasswordPolicyValidator Duplication (reviewed and accepted)

Unlike `IndianMobileNumberValidator` (T026, consolidated into `com.cms.common`), `PasswordPolicyValidator` was **kept as a separate, near-identical copy** in `com.cms.patient.account` rather than sharing 001's `com.cms.identity.account.PasswordPolicyValidator`. Reviewed and accepted: FR-008 requires "no shared... authentication logic" between the two identity systems, and a password policy — unlike a generic phone-number format check — is authentication-flow logic the two systems might legitimately want to evolve independently. Consolidating it would reintroduce exactly the coupling FR-008 rules out. Both copies are documented in code as intentional, not accidental duplication.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational. Tests (T007–T014) before implementation (T015–T024).
- **User Story 2 (Phase 4)**: Depends on Foundational AND on US1's `PatientAccountService` existing (T017) — it extends the same signup flow rather than being fully independent, since mobile is a field on the same signup request.
- **Polish (Final Phase)**: Depends on both user stories.

### Parallel Opportunities

- T002 (frontend scaffold) parallel with T001 (backend dependency).
- T004, T005, T006 (Foundational) run in parallel — different files.
- All eight US1 test tasks (T007–T014) run in parallel.
- T015 (entity) has no peer entity to parallelize with in this feature (only one entity).
- T021, T022 (Signup/Login forms) run in parallel.
- Backend (T015–T020) and frontend (T021–T024) implementation tracks are independent and can proceed in parallel once Foundational is done.

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Integration test — signup happy path in backend/src/test/java/com/cms/patient/integration/PatientSignupHappyPathTest.java"
Task: "Integration test — login in backend/src/test/java/com/cms/patient/integration/PatientLoginTest.java"
Task: "Integration test — duplicate email in backend/src/test/java/com/cms/patient/integration/PatientSignupDuplicateEmailTest.java"
Task: "Integration test — cross-system email in backend/src/test/java/com/cms/patient/integration/PatientAccountCrossSystemEmailTest.java"
Task: "Integration test — password policy in backend/src/test/java/com/cms/patient/integration/PatientSignupPasswordPolicyTest.java"
Task: "Unit test — JWT scope in backend/src/test/java/com/cms/patient/account/JwtServiceScopeTest.java"
Task: "Frontend test — forms in frontend/tests/patient-account/PatientAccountForms.test.tsx"
Task: "Contract test — no excluded fields in backend/src/test/java/com/cms/patient/contract/PatientAccountContractTest.java"
```

---

## Implementation Strategy

### MVP First — User Story 1

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational.
3. Complete Phase 3: User Story 1 — tests first (T007–T014), then implementation (T015–T024).
4. **STOP and VALIDATE** US1 independently (signup + login work, mobile not yet supported).
5. Layer in User Story 2 (T025–T028) — optional mobile number.
6. Polish (T029–T031).

### Notes

- Verify each test in T007–T014 and T025 actually fails before writing its corresponding implementation (Constitution Principle I).
- The DB-level unique constraint from T003 is what T009 is specifically proving — the app-level pre-check alone (in T017) is not sufficient per Constitution Principle IV.
- T026's relocation of `IndianMobileNumberValidator` touches 001's code — re-run 001's existing test suite after T026 to confirm no regression there.
