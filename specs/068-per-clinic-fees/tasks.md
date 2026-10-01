---

description: "Task list for 068 per-clinic fees"
---

# Tasks: Per-Clinic Fees

**Input**: Design documents from `/specs/068-per-clinic-fees/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/clinic-fees-api.md, quickstart.md

**Tests**: Required, test-first (Constitution I). Each test task is run **before** its implementation task, and its observed result is recorded as `(observed: RED|GREEN)`.

**Organization**: By user story. US1 is resolution and isolation, US2 authorization, US3 the upgrade copy, and US4 the readiness and visible prices.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US4 (spec.md)
- Paths are relative to the repository root.

**Run a test class:** `cd backend && ./gradlew test -x spotlessApply --tests "*<Class>"`. Docker is needed. Never run `spotlessApply` across the tree.

---

## Phase 1: Setup

- [X] T001 Confirm the branch is based on current `main`, and that the backend compiles (`./gradlew compileJava compileTestJava -x spotlessApply`).

---

## Phase 2: Foundational (blocks every story)

- [X] T002 Write `backend/src/test/java/com/cms/booking/integration/ClinicFeeSchemaTest.java`, using an existing integration base with a Postgres `@Container`. Assert that:
  - `clinic_doctor_fee` and `clinic_appointment_type_price` exist;
  - a second row for the same (clinic, doctor) or (clinic, type) is rejected by the unique keys;
  - a negative amount is rejected by the CHECK.

  Record the result; it is RED because the tables don't exist yet.
- [X] T003 Create `backend/src/main/resources/db/migration/V42__create_clinic_fee_tables.sql` per data-model.md: both tables, FKs, `uq_clinic_doctor_fee`, `uq_clinic_appointment_type_price`, `CHECK (amount >= 0)`, and `updated_by_account_id` nullable FK to `account`. Do not touch V9.
- [X] T004 [P] Add the entities `ClinicDoctorFee` and `ClinicAppointmentTypePrice` in `backend/src/main/java/com/cms/booking/domain/`, and their repositories in `backend/src/main/java/com/cms/booking/repository/`:
  - `findByClinic_IdAndDoctorProfile_Id`;
  - `findByClinic_IdAndAppointmentType_Id`;
  - `findByClinic_IdAndAppointmentType_DoctorProfile_Id`;
  - `existsBy…` / `countBy…DoctorProfile_Id` for the guard;
  - `deleteByClinic_Id`.
- [X] T005 Re-run T002: it must be GREEN. Then run the whole build. Hibernate `validate` must pass with V42.

---

## Phase 3: User Story 1 — a clinic's own prices drive its bookings (P1) 🎯

**Goal:** FR-001 to FR-004 and FR-009. Every booking path resolves the price from the booking's clinic, with no fallback.

- [X] T006 [P] [US1] Rewrite `backend/src/test/java/com/cms/booking/unit/FeeResolutionServiceTest.java` (or the existing 017 resolution test) for the new signature `resolve(clinicId, doctorProfileId, appointmentTypeId)`. Cover:
  - the clinic type price wins;
  - otherwise the clinic default is used;
  - otherwise `NoFeeConfiguredException`, **even if another clinic has prices** (FR-004);
  - a type that isn't the doctor's is refused.

  Expect RED (compile).
- [X] T007 [P] [US1] Write `backend/src/test/java/com/cms/booking/integration/PerClinicFeeBookingTest.java`. One doctor at clinics A and B with defaults 300 and 500:
  - a patient fixed-time booking at A locks 300 and one at B locks 500;
  - after A changes to 350, B's next booking still locks 500 (SC-001);
  - a type price at A only is used at A, and B uses its default;
  - with no prices at B, booking at B is blocked with `NO_FEE_CONFIGURED` while A still books.

  Expect RED.
- [X] T008 [US1] Implement the resolution in `backend/src/main/java/com/cms/booking/service/FeeResolutionService.java`: `resolve(UUID clinicId, UUID doctorProfileId, UUID appointmentTypeId)` using the clinic repositories only (data-model "Resolution").
- [X] T009 [US1] Pass the booking's `clinicId` at all five call sites:
  - `PatientBookingService` (this also covers the waitlist claim);
  - `PatientQueueBookingService`;
  - `StaffBookingService`;
  - `StaffQueueBookingService`;
  - `FrontDeskWalkInService`.
- [X] T010 [US1] **Test fixtures.** Update the 6 base-class helpers `saveAppointmentTypeWithOverride` / `saveAppointmentTypeWithNoOverride`, and the `DoctorDefaultFee` seeding, under `backend/src/test/java/**/Abstract*IntegrationTest.java` so they write **clinic** prices for every clinic where the doctor holds an active Doctor role (mirroring V43). Fix any remaining direct seeders that `grep -rl "DoctorDefaultFee(\|fee_override\|feeOverride" backend/src/test` finds, and record the file count.
- [X] T011 [US1] Re-run T006, T007, and every `com.cms.booking.*` and `com.cms.waitlist.*` test: GREEN.

**Checkpoint:** the money is clinic-correct on all six booking paths.

---

## Phase 4: User Story 2 — only that clinic's admin can set its prices (P1)

**Goal:** FR-005, FR-006, FR-007 and FR-012.

- [X] T012 [P] [US2] Write the `@WebMvcTest` `backend/src/test/java/com/cms/booking/contract/ClinicFeeControllerContractTest.java` for the 4 endpoints in contracts/clinic-fees-api.md, covering success, 400 `INVALID_FEE_AMOUNT`, 403 and 404. Expect RED.
- [X] T013 [P] [US2] Write the integration test `backend/src/test/java/com/cms/booking/integration/ClinicFeeAuthorizationTest.java`. Only (a) succeeds:
  - (a) A's ClinicAdmin sets A's prices → 200;
  - (b) B's ClinicAdmin, the doctor also being staffed at B, → 403, and A's prices are unchanged;
  - (c) the doctor → 403;
  - (d) A's Operations staff → 403 on writes and 200 on GET;
  - (e) A's admin for a doctor not staffed at A → 403;
  - (f) no token → 401.

  Expect RED.
- [X] T014 [P] [US2] Add to the existing appointment-type tests in `backend/src/test/java/com/cms/booking/`:
  - `PUT /api/v1/doctors/{id}/default-fee` → 410 `FEE_MOVED_TO_CLINIC`;
  - `POST …/appointment-types` with a non-null `feeOverride` → 400 `FEE_MOVED_TO_CLINIC`;
  - create without a fee and rename → unchanged.

  Expect RED.
- [X] T015 [US2] Implement `backend/src/main/java/com/cms/booking/service/ClinicFeeService.java`: get, setDefault, setTypePrice and removeTypePrice. Writes check that the caller is an active ClinicAdmin **of clinicId** and that the doctor is actively staffed there; reads require active staff of clinicId. Writes upsert on the unique key and set `updated_by_account_id`.
- [X] T016 [US2] Implement `backend/src/main/java/com/cms/booking/api/ClinicFeeController.java` at `/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees/**`, with exception mappings for `INVALID_FEE_AMOUNT` and `FORBIDDEN`. No security-config change: the staff chain already authenticates `/api/v1/clinics/**`.
- [X] T017 [US2] Retire the doctor-wide price writes in `BookingController` / `AppointmentTypeService` (410 / 400 `FEE_MOVED_TO_CLINIC`). Type creation stores no price.
- [X] T018 [US2] Re-run T012–T014: GREEN.

---

## Phase 5: User Story 3 — nothing changes on day one (P1)

**Goal:** FR-008, FR-009 and SC-003.

- [X] T019 [US3] Write `backend/src/test/java/com/cms/booking/integration/ClinicFeeUpgradeCopyTest.java`. Use Flyway's `target` to migrate to **V41**, then seed the old shape with SQL:
  - a doctor at active clinics A and B and inactive clinic C;
  - a default of 500 and a type override of 800;
  - a second doctor with no default;
  - one existing booking with a locked fee.

  Then migrate to V43, and assert:
  - A and B each have default 500 and type price 800;
  - C has none;
  - the no-default doctor has no clinic default;
  - the booking's locked fee is unchanged;
  - `resolve()` matches the pre-upgrade amount for every (doctor, active clinic, type).

  Expect RED.
- [X] T020 [US3] Create `backend/src/main/resources/db/migration/V43__copy_doctor_fees_to_active_clinics.sql`: insert-only copies joining `doctor_profile.account_id` → `role_assignment` (`role = 'Doctor' AND active`), per data-model "Copy (V43)".
- [X] T021 [US3] Re-run T019: GREEN.

---

## Phase 6: User Story 4 — staff and patients see the clinic's prices (P2)

**Goal:** FR-010 and FR-011.

- [X] T022 [P] [US4] Write tests (expect RED):
  - **Readiness:** a doctor priced at A but not at B is ready at A and "setup incomplete" at B, via `GET /api/v1/clinics/{id}/doctors/booking-readiness` or its existing path.
  - **Patient listing:** `GET /api/v1/patients/clinics/{clinicId}/doctors/{doctorProfileId}/appointment-types` returns each type's effective `fee` at that clinic.
  - **Embedded listings:** the open-slot and queue-session listings carry the clinic's `fee`.
- [X] T023 [US4] `DoctorBookingReadinessService.forClinic` reads the clinic tables (data-model "Readiness").
- [X] T024 [US4] Change `AppointmentTypeResponse` from `feeOverride` to `fee` (the effective fee in the clinic context). Add the clinic-scoped patient endpoint in `PatientBookingController`, and make the clinic-scoped embedded listings use it. The old doctor-only patient path returns `fee: null`.
- [X] T025 [US4] Frontend:
  - `frontend/src/features/appointment-types/`: the price editor edits the current clinic's prices through the new endpoints, read-only for non-admins;
  - booking forms (`patient-booking`, `staff-booking`, `waitlist`, `front-desk-walk-in`): read `fee` and call the clinic-scoped listing;
  - update the affected Vitest tests.
- [X] T026 [US4] Re-run T022 plus `cd frontend && npx tsc -b && npm run lint && npx vitest run`: GREEN.

---

## Phase 7: Guards, docs, polish

- [X] T027 [P] Doctor delete guard (`DoctorVerificationService.deleteGuarded`): also block on clinic price rows for the doctor or the doctor's types. Clinic permanent delete (`ClinicVerificationService`): also `deleteByClinic_Id` both price tables. Add a test for each.
- [X] T028 [P] Fix the `OpenApiConfig` description: `/api/v1/doctors/**` is staff-authenticated, not "public".
- [X] T029 Run `cd backend && ./gradlew spotlessCheck -x spotlessApply`, then the full backend suite (it should have no new failures).
- [X] T030 Run the quickstart.md §2 runtime walk-through against a fresh database, including the V43 copy and the US1/US2/FR-012 calls.
- [X] T031 [P] Docs:
  - audit doc 08: SEC-03 → FIXED (068);
  - `backlog/progress.md`: a 068 row;
  - `HANDOFF.md`: a new part;
  - `CODEX_HANDOFF.md`.
- [X] T032 Check off tasks with their observed results, confirm the exact changed files with `git status`, then commit, push and open the PR.

## Dependencies

- **T001 → T002 → T003/T004 → T005** (foundation) block all stories.
- **US1 (T006–T011)** blocks US4's display (T024), because the effective fee uses the resolution logic.
- **US2 (T012–T018)** depends on the foundation only.
- **US3 (T019–T021)** depends on V42 (T003).
- **T010 (fixtures)** must land with T008/T009, or every booking test goes red.
- **Polish (T027–T032)** comes last.

## Parallel opportunities

- T006/T007, T012/T013/T014 and T022 are separate new test files.
- T027 and T028 are independent.

## MVP

The minimum complete delivery is **US1 + US2 + US3**: correct money, correct permissions and a no-change upgrade. **US4** completes the visible prices, and must ship in the same PR so that no screen shows prices from the wrong clinic.

## Observed results (2026-10-01, IST)

- **T001–T005:** branch on `main` @ `4c2a407`; `ClinicFeeSchemaTest` red on the missing tables, then green with V42; Hibernate `validate` passes.
- **T006–T009:** `FeeResolutionServiceTest` (5) and `PerClinicFeeBookingTest` (4) red on the new `resolve(clinicId, …)` signature, then green; all five booking call sites pass the booking's clinic.
- **T010 / T011:** the fixtures now seed clinic prices through one test bean, `ClinicPriceFixtures` (it mirrors V43 in SQL). Changes: the 3 base-class `saveAppointmentTypeWithOverride` helpers, 28 direct seeding sites in 22 files, and the price-row cleanup in 11 base classes (the 12th, `AbstractBookingIntegrationTest`, was done in T008). There were no `new DoctorDefaultFee(` sites. Full backend suite on that state: **1,081 passed, 0 failed** (`spotlessCheck` green).
- **T012–T018:** the contract (8), authorization (10) and retired-write tests pass. `SetDefaultFeeTest` was deleted with the service method it tested. Its upsert guarantee is now `ClinicFeeAuthorizationTest.settingTheDefaultTwiceReplacesRatherThanDuplicates`, and the upsert is an atomic `INSERT … ON CONFLICT` (no unique-key 500 under a race).
- **T019–T021 deviation:** `ClinicFeeUpgradeCopyTest` follows the repo's existing migration-test pattern (`QueueTokenMigrationTest`): it seeds pre-068 rows and re-runs V43's own SQL. It does **not** rewind the shared Testcontainers database with Flyway `target`. The real V41 → V43 upgrade was proven at runtime instead (T030).
- **TDD note:** the US2–US4 tests were written before their code, but in the same build cycle (a full-suite run held the build), so I did not watch them go red. To compensate, I ran a mutation check: removing `ClinicFeeService`'s clinic-admin check fails 3 of the `ClinicFeeAuthorizationTest` cases. I then restored the file (byte-identical).
- **T022–T026:**
  - Backend: `PerClinicFeeVisibilityTest` (4), the updated `DoctorBookingReadinessServiceTest`, and 68 targeted tests, all green.
  - Frontend: `tsc` clean; lint exit 0 with no warnings in the touched files; Vitest **445/445**.
- **T027 / T028:** the clinic delete clears both price tables (unit `RejectedClinicDeletionGuardTest` plus integration `ClinicPriceDeletionGuardTest`). A clinic default fee blocks a rejected doctor's delete. The `OpenApiConfig` label is corrected.
- **T030:** run against a fresh Postgres 16.
  - Migrated to V41 and seeded the pre-068 shape (doctor at A and B, default 500, Procedure override 800, an open slot at each).
  - Started the 068 jar: Flyway applied V42 and V43, and `validate` passed.
  - Fees at A and B were both 500/800 (copied, `updated_by` null).
  - A's admin set A's default to 300. A booking at A locked 300; a booking at B locked 500.
  - B's admin got 403 on A's fees, and so did the doctor; A's fee stayed 300.
  - `PUT /doctors/{d}/default-fee` returned 410, and creating a type with a fee returned 400.
  - The patient clinic listing showed 300/800 at A and 500/800 at B.
  - Readiness at B flagged a new doctor with an unpriced type as not booking-ready.
- **T029:** final full backend suite **1,108 passed, 0 failed, 0 skipped**, `spotlessCheck` green. After that, the clearer clinic-fee 403 messages were re-verified with the targeted set: 50/50.
- **T031:** audit docs 08, 04, 02 and 10; `backlog/progress.md`; `HANDOFF.md` Part 17; `CODEX_HANDOFF.md`.

## Phase 8: Convergence

- [ ] T033 Let the doctor concerned read their own prices at a clinic even when they are not (or are no longer) active staff there: `ClinicFeeService.get` allows the caller if they are active staff of the clinic **or** the account of `doctorProfileId`; cover it in `ClinicFeeAuthorizationTest` per FR-006 (partial)
- [ ] T034 Remove the now-unused doctor-wide readiness queries `AppointmentTypeRepository.findDoctorProfileIdsWithAnAppointmentTypeMissingFeeOverride` and `DoctorDefaultFeeRepository.findDoctorProfileIdsWithDefaultFee`, and update the javadoc in `ClinicAppointmentTypePriceRepository` that cites the first, per FR-012 / Constitution II (unrequested)

