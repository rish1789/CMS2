---

description: "Task list for Patient Record Auto-Creation & Phone-Based Linking"
---

# Tasks: Patient Record Auto-Creation & Phone-Based Linking

**Input**: Design documents from `/specs/009-patient-record-phone-linking/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/patient-linking-service.md, quickstart.md

**Tests**: Included and REQUIRED per Constitution Principle I.

**Organization**: Three user stories — US1 (P1, phone-match-and-link, including the linked-record protection), US2 (P1, no-match creates new, and repeat-call reuse), US3 (P2, same-account concurrency). US1's implementation task builds the whole non-race-safe core method in one pass (its two branches — existing-link check and phone-match — don't split further without being contrived); US2 validates behavior US1's task already provides; US3 adds the genuinely separable race-catch/retry extension on top. No frontend, no HTTP endpoint — this feature is backend-service-only (plan.md).

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T001 Create Flyway migration `V5__create_patient.sql` — `patient` table (`id`, `clinic_id` FK, `patient_account_id` nullable FK, `name`, `phone` nullable, `created_at`) plus the two partial unique indexes `uq_patient_clinic_account` (on `patient_account_id IS NOT NULL`) and `uq_patient_clinic_phone_unlinked` (on `patient_account_id IS NULL`) — data-model.md in `backend/src/main/resources/db/migration/`
- [X] T002 [P] Implement `Patient` entity in `backend/src/main/java/com/cms/patient/record/Patient.java` (depends on T001)
- [X] T003 [P] Implement `PatientAccountNotFoundException` in `backend/src/main/java/com/cms/patient/record/PatientAccountNotFoundException.java`
- [X] T004 Implement `PatientRepository` — `findByClinic_IdAndPatientAccount_Id`, `findByClinic_IdAndPhoneAndPatientAccountIsNull` in `backend/src/main/java/com/cms/patient/record/PatientRepository.java` (depends on T002)
- [ ] T005 Integration test — proves migration T001's `uq_patient_clinic_phone_unlinked` constraint directly: two unlinked `Patient` rows inserted via `PatientRepository` for the same clinic+phone, the second commit is rejected (Constitution Principle I: "a migration that enforces a new invariant... MUST have a test proving the invariant holds"; this feature's own service never exercises this constraint — research.md decision #1 — so it needs its own direct proof) in `backend/src/test/java/com/cms/patient/record/integration/PatientTableUnlinkedPhoneUniquenessTest.java` (depends on T004) — **written, compiles; unexecuted (Testcontainers/Docker sandbox limitation, consistent with 001–008; also hit and fixed the known OneDrive `backend/build` corruption issue this session)**

**Checkpoint**: Foundation ready.

---

## Phase 2: User Story 1 - First Booking Reuses a Matching Walk-In Record (Priority: P1) 🎯 MVP

**Goal**: A phone-matching, not-already-linked-elsewhere walk-in Patient record is found and linked automatically; a phone match against a record already linked to a *different* account is protected (never linked, a new record is created instead).

**Independent Test**: Create a walk-in Patient record with a known phone; call `findOrCreatePatient` for an account with that same phone at that clinic; confirm the existing record is returned and now linked.

### Tests for User Story 1 ⚠️

> Write these tests FIRST; confirm they FAIL before starting implementation below.

- [ ] T006 [P] [US1] Integration test — a phone-matching, unlinked walk-in record is reused and linked, prior data preserved (AC1, FR-003, SC-001) in `backend/src/test/java/com/cms/patient/record/integration/PatientLinkingPhoneMatchTest.java` — **written, compiles; unexecuted (Testcontainers/Docker sandbox limitation)**
- [ ] T007 [P] [US1] Integration test — the same account's Patient records at two different clinics are independent (never merged, each reflects only its own clinic) (AC2, FR-007, SC-005) in `backend/src/test/java/com/cms/patient/record/integration/PatientLinkingClinicScopeIndependenceTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T008 [P] [US1] Integration test — a phone match against a record already linked to a *different* account is not reused; a new, separate record is created instead (AC3, FR-003's protection clause, SC-006) in `backend/src/test/java/com/cms/patient/record/integration/PatientLinkingProtectedLinkedRecordTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T009 [P] [US1] Integration test — an account with no phone number on file never matches anything; a new record is created with a `null` phone (Edge Cases, FR-008) in `backend/src/test/java/com/cms/patient/record/integration/PatientLinkingNoPhoneTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T010 [P] [US1] Integration test — an unknown `patientAccountId` throws `PatientAccountNotFoundException` in `backend/src/test/java/com/cms/patient/record/integration/PatientLinkingAccountNotFoundTest.java` — **written, compiles; unexecuted (same sandbox limitation)**

### Implementation for User Story 1

- [X] T011 [US1] Implement `PatientLinkingService.findOrCreatePatient(UUID patientAccountId, UUID clinicId, String name)` — loads the `PatientAccount` (or throws `PatientAccountNotFoundException`); checks for an existing link at this clinic (FR-002); if none, and the account has a phone, looks for an unlinked matching record and links it (FR-003, protection clause); otherwise creates a new record (FR-004) — no race-handling yet (added by US3) in `backend/src/main/java/com/cms/patient/record/PatientLinkingService.java` (depends on T004, T003) — verified via `StaffOnboardingContractTest` (no Docker needed) still passing, no regression

**Checkpoint**: User Story 1 is fully functional and independently testable (non-concurrent scenarios).

---

## Phase 3: User Story 2 - First Booking With No Match Creates a New Record (Priority: P1)

**Goal**: With no matching record at all, a new clinic-scoped Patient record is created and linked; a repeated call for an already-linked account+clinic reuses that link directly, with no re-matching.

**Independent Test**: Call `findOrCreatePatient` for an account/clinic with no existing Patient record; confirm a new one is created, scoped and linked correctly. Call it again; confirm the same record is returned, not a new one.

### Tests for User Story 2 ⚠️

- [ ] T012 [P] [US2] Integration test — no matching record exists: a new Patient record is created, clinic-scoped, linked, with the caller-supplied name and the account's phone (AC1, FR-004, SC-002) in `backend/src/test/java/com/cms/patient/record/integration/PatientLinkingNewRecordTest.java` — **written, compiles; unexecuted (Testcontainers/Docker sandbox limitation)**
- [ ] T013 [P] [US2] Integration test — an account already linked at a clinic gets the same record back on a repeat call, with no phone re-matching and no new row, even if a different `name` argument is passed (AC2, FR-002, SC-003) in `backend/src/test/java/com/cms/patient/record/integration/PatientLinkingReuseExistingLinkTest.java` — **written, compiles; unexecuted (same sandbox limitation)**

### Implementation for User Story 2

No new implementation — T011 (US1) already implements both the existing-link check (FR-002) and the create-new fallback (FR-004) as part of the same cohesive method. This phase is validation-only.

**Checkpoint**: Both user stories functional together.

---

## Phase 4: User Story 3 - Concurrent First Bookings for the Same Account Never Duplicate a Record (Priority: P2)

**Goal**: Two concurrent `findOrCreatePatient` calls for the same account+clinic (no pre-existing record) both succeed, and exactly one Patient record exists afterward.

**Independent Test**: Issue two concurrent calls for the same new account+clinic; confirm both succeed and return the same record id, with only one row committed.

### Tests for User Story 3 ⚠️

- [ ] T014 [P] [US3] Integration test — two concurrent calls for the same account+clinic (no pre-existing record) both succeed, return the same `Patient` id, and exactly one row exists afterward (AC1, FR-005a/FR-006, SC-004) in `backend/src/test/java/com/cms/patient/record/integration/PatientLinkingSameAccountRaceTest.java` — **written, compiles; unexecuted (same sandbox limitation)**

### Implementation for User Story 3

- [X] T015 [US3] Extend `findOrCreatePatient`'s create-new step with a `try/catch (DataAccessException)` — on a `uq_patient_clinic_account` violation, re-run the existing-link lookup (FR-002's query) and return that record instead of failing (FR-006) in `backend/src/main/java/com/cms/patient/record/PatientLinkingService.java` (depends on T011) — verified via `StaffOnboardingContractTest` (no Docker needed) still passing, no regression

**Checkpoint**: All three user stories independently functional.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T016 [P] Run all `quickstart.md` scenarios end-to-end (direct service calls, no HTTP layer — Testcontainers-backed) — **not done**; same Docker root cause as T005–T014 above. What could be verified without Docker was: `StaffOnboardingContractTest` (web-layer only) still passing twice (after T011 and after T015), full backend `compileJava`/`compileTestJava`/`build` clean
- [X] T017 [P] Add structured logging for linking outcomes (matched-and-linked / protected-and-created-new / created-new / race-recovered — account id and clinic id only, never the phone number value)
- [X] T018 Security/privacy review pass: confirm the linked-record protection (T008) genuinely prevents one account from ever reading another's linked `Patient` row's data, and that no log line or exception message ever contains a phone number (Constitution Principle IV) — verified: all `log.info` calls in `PatientLinkingService` log only account/clinic ids, never phone or name; `PatientAccountNotFoundException`'s message contains only the UUID; the repository query backing FR-003 (`findByClinic_IdAndPhoneAndPatientAccountIsNull`) structurally cannot return a row linked to a different account, by construction

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — BLOCKS all three user stories.
- **User Story 1 (Phase 2)**: Depends on Foundational. Tests (T006–T010) before implementation (T011).
- **User Story 2 (Phase 3)**: Depends on Foundational AND on US1's T011 existing — it validates behavior T011 already provides, rather than adding new code.
- **User Story 3 (Phase 4)**: Depends on Foundational AND on US1's T011 (extends it). Test (T014) before implementation (T015).
- **Polish (Final Phase)**: Depends on all three user stories.

### Parallel Opportunities

- T002, T003 (Foundational) run in parallel; T004 depends on T002; T005 depends on T004.
- All five US1 test tasks (T006–T010) run in parallel.
- Both US2 test tasks (T012–T013) run in parallel, and can be written/attempted as soon as Foundational is done (they don't depend on T011 being written first, only on it passing before the phase is considered complete).

---

## Implementation Strategy

### MVP First — User Story 1

1. Complete Phase 1: Foundational (including T005, proving the schema's own constraint independent of any consuming feature).
2. Complete Phase 2: User Story 1 — tests first (T006–T010), then implementation (T011).
3. **STOP and VALIDATE** US1 independently (phone-matching and linked-record protection work; no-match/repeat-call behavior technically already works too, via the same method, though not yet explicitly proven).
4. Layer in User Story 2 (T012–T013) — pure validation of behavior T011 already provides.
5. Layer in User Story 3 (T014–T015) — race-safety extension.
6. Polish (T016–T018).

### Notes

- Verify each test in T006–T010, T012–T013, and T014 actually fails before writing its corresponding implementation (Constitution Principle I) — for T012/T013 specifically, this means confirming they'd fail against a stub that always throws `UnsupportedOperationException`, since T011 will already exist by the time these are checked off.
- T005 is a Foundational-phase test proving a migration invariant directly, independent of any user story's own behavior — an intentional exception to "tests belong to a user story," justified because no user story in this feature ever exercises that code path (research.md decision #1); it's here purely to satisfy Constitution Principle I's migration-testing mandate before 016/018/020 come to depend on it.
- T008 is the test proving the Clarify-stage privacy decision (FR-003's protection clause) — the single highest-stakes correctness property in this feature; don't shortcut it.
- T014/T015 are what actually prove Constitution Principle IV's data-layer race guarantee for the *same-account* case — don't shortcut to an app-level-only check (e.g. a pre-check `exists()` call with no DB constraint backing it, which `uq_patient_clinic_account` from T001 exists specifically to prevent from being sufficient).
