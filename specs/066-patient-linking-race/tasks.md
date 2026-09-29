---

description: "Task list for 066 patient-linking same-account race"
---

# Tasks: Patient-Linking Same-Account Race

**Input**: Design documents from `/specs/066-patient-linking-race/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/patient-linking-service.md, quickstart.md

**Tests**: Required. Constitution Principle I is test-first, and spec SC-005 names the red test. Every test task is written and **run before** the implementation task, and its observed result is recorded in the task line as `(observed: RED|GREEN)`. A test that is green before the change is a characterization test (research.md R4), not proof of the fix. Record it as such; do not delete it.

**Organization**: By user story. US1 (P1) is the fix; US2 (P2) guards the all-or-nothing booking guarantee.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 or US2 (spec.md)
- All paths are relative to the repository root. The backend lives in `backend/`.

**How to run a test class**: `cd backend && ./gradlew test -x spotlessApply --tests "*<ClassName>"`. This needs Docker for Testcontainers. Never run `spotlessApply` across the tree.

---

## Phase 1: Setup

**Purpose**: Confirm the starting point, so that red and green results are attributable to this feature.

- [ ] T001 Confirm that branch `claude/066-patient-linking-race` is based on `main` `715738b` and that Docker is reachable (`docker info`). Then run `cd backend && ./gradlew test -x spotlessApply --tests "*PatientLinkingSameAccountRaceTest"` and confirm it **fails** with `current transaction is aborted` (research.md R1). Record the result here.

---

## Phase 2: Foundational

**Purpose**: None. There is no schema change, no new entity and no shared infrastructure (data-model.md). The user-story phases can start after T001.

---

## Phase 3: User Story 1 - Two simultaneous first bookings at a new clinic both succeed (Priority: P1) 🎯 MVP

**Goal**: Concurrent `findOrCreatePatient` calls for the same Patient Account and clinic all succeed and share one Patient record, on both booking paths (FR-001, FR-002, FR-004, FR-006, FR-007).

**Independent Test**: The quickstart.md §1 and §2 test classes pass, and exactly one `patient` row exists per scenario.

### Tests for User Story 1 (write first, run, and record the result before T006)

- [ ] T002 [P] [US1] Leave `backend/src/test/java/com/cms/patient/record/integration/PatientLinkingSameAccountRaceTest.java` **unchanged** (SC-005). It is the primary red test, already confirmed in T001. No edit.
- [ ] T003 [P] [US1] Create `backend/src/test/java/com/cms/patient/record/integration/PatientLinkingWinnerRollbackTest.java`, extending `AbstractPatientRecordIntegrationTest` (FR-004).
  - Inject `PatientLinkingService` and a `TransactionTemplate` (built from the autowired `PlatformTransactionManager`).
  - **Thread A:** inside `transactionTemplate.execute`, call `findOrCreatePatient(account, clinic, "Jane Doe")`, count down latch `aLinked`, wait on latch `releaseA` (max 30 s), then `status.setRollbackOnly()`.
  - **Thread B:** after `aLinked`, call `findOrCreatePatient` for the same account and clinic in its own transaction (no template).
  - **Main thread:** wait about 500 ms so that B is blocked, then count down `releaseA`.
  - **Assert:** B returns normally with a non-null id; `patientRepository.count() == 1`; the one row's id equals B's returned id.
  - Use a 2-thread `ExecutorService` and `shutdownNow()` in `finally`.
  - Record `(observed: …)`. Research R4 says this may be green before the fix, because a pre-fix B's INSERT waits and then succeeds after A's rollback. Green here is acceptable as characterization.
- [ ] T004 [P] [US1] Create `backend/src/test/java/com/cms/booking/integration/PatientQueueBookingSameAccountRaceTest.java`, extending `AbstractQueueBookingIntegrationTest` (FR-007, queue path).
  - **Setup:** a new clinic and a staffed doctor; `saveQueueSession(clinic, doctor)`; an appointment type via `saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"))`; one `savePatientAccount()` with **no** Patient record at the clinic.
  - **Race:** fire two concurrent `POST` patient queue bookings for that session with the same `patientToken(account)`. Copy the exact endpoint and body from `backend/src/test/java/com/cms/booking/integration/PatientQueueBookingTest.java`. Use `invokeAll` on a 2-thread pool, as in `QueueBookingConcurrencyTest`.
  - **Assert:** both responses are 201; the `patient` table has exactly 1 row for `(clinic, account)`; both `booking` rows reference that Patient id.
  - Record `(observed: …)`; expected RED.
- [ ] T005 [P] [US1] Create `backend/src/test/java/com/cms/booking/integration/PatientBookingSameAccountRaceTest.java`, extending `AbstractPatientBookingIntegrationTest` (FR-007, fixed-time path with the booking limit **disabled**).
  - **Setup:** in `@BeforeEach`, autowire `ProtectionSettingService` and call `update("booking-limit.enabled", "false", "test-066")`. In `@AfterEach`, delete the `protection_setting` and `protection_setting_change_log` rows, via their repositories or `JdbcTemplate`, **before** the base class cleanup, so that other test classes see defaults. Check the real table names in `backend/src/main/resources/db/migration`.
  - **Fixture:** `saveFixedTimeSessionWithSlots(clinic, doctor)`, two **different** open slots of that session, `saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"))`, and one `savePatientAccount()` with no Patient record at the clinic.
  - **Race:** fire two concurrent `POST` slot bookings, one per slot, with the same `patientToken(account)`. Copy the endpoint and body from `PatientBookingFirstTimeLinkTest.java`.
  - **Assert:** both responses are 201; exactly 1 `patient` row for `(clinic, account)`; both bookings reference it.
  - Record `(observed: …)`; expected RED.

### Implementation for User Story 1

- [ ] T006 [US1] In `backend/src/main/java/com/cms/patient/record/service/PatientLinkingService.java` `findOrCreatePatient` (research.md R3, contracts/patient-linking-service.md):
  - Replace `patientAccountRepository.findById(patientAccountId)` with `patientAccountRepository.findWithLockById(patientAccountId)`, keeping the same `orElseThrow(PatientAccountNotFoundException)`.
  - Add a comment citing 066 research R3 and the 060 precedent.
  - In the create branch, keep `saveAndFlush`, but **remove** the `try/catch (DataAccessException)` re-read block and the now-unused `isUniqueConstraintViolation` helper, together with any imports they alone used. A violation now propagates (FR-006). Replace the old catch comment with a short one explaining why no catch is needed.
  - Do not change the FR-002 or FR-003 branches.
- [ ] T007 [US1] Update the javadoc of `findWithLockById` in `backend/src/main/java/com/cms/patient/account/repository/PatientAccountRepository.java` to name its second user: 066, `PatientLinkingService`. This is a comment-only change.
- [ ] T008 [US1] Check the Mockito unit tests for `PatientLinkingService`. Run `grep -rn "PatientLinkingService" backend/src/test/java --include=*Test.java`, and in any **unit** test that stubs `patientAccountRepository.findById` for `findOrCreatePatient`, switch the stub or verify to `findWithLockById`. If none exist, record "none".
- [ ] T009 [US1] Re-run T001's test and T003–T005. All must pass. Then run the existing 009 linking suite, `--tests "com.cms.patient.record.integration.PatientLinking*"`, and record the results. SC-001, SC-002, SC-004 and SC-005 are covered for linking.

**Checkpoint**: US1 is complete. Both concurrent callers succeed on all paths, with exactly one Patient record.

---

## Phase 4: User Story 2 - A failed booking leaves no stray patient record (Priority: P2)

**Goal**: Prove, and keep proving, that the fixed-time booking stays all-or-nothing (FR-003, SC-003).

**Independent Test**: A fixed-time first booking that fails after linking leaves zero `patient` rows for that account and clinic.

### Tests for User Story 2

- [ ] T010 [P] [US2] Create `backend/src/test/java/com/cms/booking/integration/PatientBookingFailureLeavesNoPatientTest.java`, extending `AbstractPatientBookingIntegrationTest`.
  - **Fixture:** a clinic, a doctor and a fixed-time session; one open slot `S`; an appointment type with a fee.
  - **Conflict:** insert, directly through `JdbcTemplate`, a `booking` row that occupies `S` while `S.status` stays `OPEN`. The booking INSERT inside `doBookSlot` then hits `uq_booking_slot` **after** `findOrCreatePatient` has run. Take the exact column list from the booking migrations; the conflicting row needs its own existing Patient, so use `saveExistingPatient(clinic)`.
  - **Act:** book `S` as a new `savePatientAccount()` that has no Patient record at the clinic.
  - **Assert:** the response is the existing slot-taken error (409 `SLOT_ALREADY_BOOKED`; copy the code from `PatientBookingAlreadyBookedTest.java`); `patientRepository.findByClinic_IdAndPatientAccount_Id(clinic, account)` is empty.
  - Record `(observed: …)`. Expected GREEN before and after T006: this is a characterization guard (research R4), and it must stay green.

### Implementation for User Story 2

- [ ] T011 [US2] No production change. Confirm that T010 is green **after** T006, which proves the account lock did not move the Patient record out of the booking transaction.

**Checkpoint**: US1 and US2 are both independently verified.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T012 Run `cd backend && ./gradlew spotlessCheck -x spotlessApply`. Fix formatting only in files this feature touched.
- [ ] T013 Run the regression scope from quickstart.md §3 (`--tests "com.cms.patient.*" --tests "com.cms.booking.*"`) and compare with `main` `715738b`: no new failures. `BookingRateLimitConcurrencyTest` is known-failing on `main` and unrelated.
- [ ] T014 Run the full suite (quickstart.md §4) and record the failure list. `PatientLinkingSameAccountRaceTest` must be absent, with no failure that is absent on `main`.
- [ ] T015 [P] Update `docs/product-audit/07-BUG-AND-DEFECT-REGISTER.md`: mark the "Patient linking race" entry as fixed by 066, with the test names.
- [ ] T016 [P] Update `backlog/progress.md` if it tracks bug-fix features. Otherwise record "n/a".
- [ ] T017 Check off completed tasks in this file with their observed results, then commit and push to `claude/066-patient-linking-race`. Before committing, inspect `git status` and confirm the exact changed files.

---

## Dependencies & Execution Order

- **T001** comes first: it is the baseline.
- **T002–T005** (tests) come before **T006** (implementation), per Principle I. T003, T004 and T005 are separate new files, so they are marked [P].
- **T006** comes before T007, T008 and T009. T007 and T008 are small and sequential after T006. T009 is the US1 gate.
- **T010** can be written in parallel with T003–T005, since it is a different file. It must be run both before and after T006, and **T011** comes after T006.
- **T012–T014** come after T009 and T011. **T015** and **T016** are parallel docs. **T017** is last.

### User Story Dependencies

- **US1:** independent, and it is the MVP.
- **US2:** independent in its test (T010). Its "after" confirmation (T011) needs T006.

## Parallel Example

```text
# After T001, write all new tests together (different files):
T003 PatientLinkingWinnerRollbackTest.java
T004 PatientQueueBookingSameAccountRaceTest.java
T005 PatientBookingSameAccountRaceTest.java
T010 PatientBookingFailureLeavesNoPatientTest.java
```

## Implementation Strategy

- **MVP = US1:** T001 → T002–T005 (tests recorded red or green) → T006 → T009.
- **Then US2:** T010 (green before and after) and T011.
- **Then polish:** T012–T017.
- A single small production change (T006) delivers the whole fix. The rest is tests and records.

## Notes

- Do not modify shipped Flyway migrations. None is needed (data-model.md).
- Do not use `git reset`, `git clean`, `git stash` or `git checkout`. Use `-x spotlessApply`. After each file write, read the file back and check for truncation.
