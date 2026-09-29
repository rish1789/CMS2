---

description: "Task list for Doctor License Edit Triggers Re-Verification Reset"
---

# Tasks: Doctor License Edit Triggers Re-Verification Reset

**Input**: Design documents from `/specs/008-doctor-license-reverification-reset/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/doctor-profile-edit.md, quickstart.md

**Tests**: Included and REQUIRED per Constitution Principle I.

**Organization**: Two user stories — US1 (P1, editing the license number resets verification) and US2 (P2, editing any other field never does). No Setup phase: this feature adds zero new directories, dependencies, or migrations — it extends 005/007's existing `DoctorVerificationController`/`Service`/`DoctorProfile` entity directly.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T001 Add `setSpecialization(String)`, `setLicenseNumber(String)`, `setExperienceYears(int)` mutators to `DoctorProfile` (`setLicenseVerified`/`setVisible` already exist from 007) in `backend/src/main/java/com/cms/identity/doctor/DoctorProfile.java`
- [X] T002 [P] Implement `DuplicateLicenseNumberException` in `backend/src/main/java/com/cms/identity/admin/DuplicateLicenseNumberException.java`
- [X] T003 [P] Implement `EditDoctorProfileRequest` DTO (`specialization`, `licenseNumber`, `experienceYears`, `visible`, all required — Bean Validation annotations, mirroring `OnboardStaffRequest`'s pattern) per `contracts/doctor-profile-edit.md` in `backend/src/main/java/com/cms/identity/admin/dto/EditDoctorProfileRequest.java`

**Checkpoint**: Foundation ready.

---

## Phase 2: User Story 1 - Super Admin Edits a Verified Doctor's License Number, Resetting Verification (Priority: P1) 🎯 MVP

**Goal**: Editing a verified Doctor Profile's license number automatically resets `licenseVerified` to `false`, in the same transaction, without touching booking/schedule state.

**Independent Test**: Verify a Doctor Profile's license (007), `PATCH` a different license number, confirm the response and the DB show `licenseVerified = false`.

### Tests for User Story 1 ⚠️

> Write these tests FIRST; confirm they FAIL before starting implementation below.

- [ ] T004 [P] [US1] Integration test — editing the license number on a verified profile resets `licenseVerified` to `false` in the same response (FR-003, SC-001) in `backend/src/test/java/com/cms/identity/admin/integration/EditDoctorLicenseResetTest.java` — **written, compiles; unexecuted (Testcontainers/Docker sandbox limitation, consistent with 001–007)**
- [ ] T005 [P] [US1] Integration test — editing the license number on an already-unverified profile leaves it `false`, no error (User Story 1 AC2) in `backend/src/test/java/com/cms/identity/admin/integration/EditDoctorLicenseAlreadyUnverifiedTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T006 [P] [US1] Integration test — after the reset, the profile is absent from `DoctorProfileRepository.findDiscoveryEligible()` (007) (SC-004) in `backend/src/test/java/com/cms/identity/admin/integration/EditDoctorLicenseDiscoveryEligibilityTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T007 [P] [US1] Integration test — editing to a license number already used by a *different* Doctor Profile is rejected `409 DUPLICATE_LICENSE_NUMBER`, neither profile's fields change (FR-007) in `backend/src/test/java/com/cms/identity/admin/integration/EditDoctorDuplicateLicenseTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T008 [P] [US1] Integration test — a non-Super-Admin actor (no credentials, or valid staff-Account credentials) is rejected `401`, zero state change (FR-001a, SC-005) in `backend/src/test/java/com/cms/identity/admin/integration/EditDoctorAuthorizationTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T009 [P] [US1] Integration test — editing an unknown `doctorProfileId` returns `404`, no state change in `backend/src/test/java/com/cms/identity/admin/integration/EditDoctorNotFoundTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T010 [P] [US1] Integration test — a request changing the license number **and** another field (e.g. specialization) in the same call still resets `licenseVerified`, and both field changes are applied (spec Edge Cases: simultaneous changes don't suppress the rule) in `backend/src/test/java/com/cms/identity/admin/integration/EditDoctorSimultaneousChangesTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T011 [P] [US1] Integration test — a request with a missing/blank required field (e.g. blank `licenseNumber`) is rejected `400`, no state change (contracts/doctor-profile-edit.md's validation error row) in `backend/src/test/java/com/cms/identity/admin/integration/EditDoctorMissingFieldTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [X] T012 [P] [US1] Frontend test — `PendingDoctorsList`'s edit action submits a license-number change and displays the resulting `licenseVerified: false` state in `frontend/tests/doctor-verification/PendingDoctorsList.test.tsx` — confirmed RED before implementation

### Implementation for User Story 1

- [X] T013 [US1] Implement `DoctorVerificationService.edit(UUID, EditDoctorProfileRequest)` — compares submitted vs. current license number, applies all four fields, resets `licenseVerified` only if the license number changed and it was `true` beforehand (no event published — research.md), catches `uq_doctor_profile_license_number` and translates to `DuplicateLicenseNumberException` in `backend/src/main/java/com/cms/identity/admin/DoctorVerificationService.java` (depends on T001, T002)
- [X] T014 [US1] Implement `PATCH /api/v1/admin/doctors/{doctorProfileId}` on `DoctorVerificationController`, returning the existing `DoctorProfileSummaryResponse` shape (007) in `backend/src/main/java/com/cms/identity/admin/DoctorVerificationController.java` (depends on T013, T003) — verified via `StaffOnboardingContractTest` (no Docker needed) still passing, no regression
- [X] T015 [US1] Wire `AdminExceptionHandler` mapping for `DuplicateLicenseNumberException → 409 DUPLICATE_LICENSE_NUMBER`, alongside the existing `DoctorProfileNotFoundException` mapping (depends on T002)
- [X] T016 [P] [US1] Implement `editDoctor` API client function (PATCH, HTTP Basic Auth) in `frontend/src/features/doctor-verification/api.ts`
- [X] T017 [US1] Add an inline "Edit" action/form to `PendingDoctorsList` — specialization/license/experience/visible fields, submits via `editDoctor`, updates the row in place with the response (including any `licenseVerified` reset) in `frontend/src/features/doctor-verification/PendingDoctorsList.tsx` (depends on T016) — verified: full frontend suite 41/41, `tsc --noEmit` clean

**Checkpoint**: User Story 1 is fully functional and independently testable.

---

## Phase 3: User Story 2 - Editing Any Other Field Never Resets Verification (Priority: P2)

**Goal**: Confirm the reset rule is precisely scoped to the license number field alone — specialization, experience, and the `visible` toggle can all change freely without affecting `licenseVerified`.

**Independent Test**: Edit a verified profile's `experienceYears` (license number unchanged), confirm `licenseVerified` is still `true` afterward.

### Tests for User Story 2 ⚠️

- [ ] T018 [P] [US2] Integration test — editing `experienceYears` alone (license number unchanged) on a verified profile leaves `licenseVerified` `true` (FR-005, SC-002) in `backend/src/test/java/com/cms/identity/admin/integration/EditDoctorOtherFieldsNoResetTest.java` — **written, compiles; unexecuted (Testcontainers/Docker sandbox limitation, consistent with 001–007)**
- [ ] T019 [P] [US2] Integration test — editing `visible` alone (license number unchanged) on a verified profile leaves `licenseVerified` `true`, and the returned `visible` reflects the new value (FR-005, closing 007's deferred write-gap) in `backend/src/test/java/com/cms/identity/admin/integration/EditDoctorVisibilityToggleTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T020 [P] [US2] Integration test — resubmitting the identical license number alongside a specialization change leaves `licenseVerified` unchanged (FR-004, edge case: no-op license value) in `backend/src/test/java/com/cms/identity/admin/integration/EditDoctorNoOpLicenseTest.java` — **written, compiles; unexecuted (same sandbox limitation)**

### Implementation for User Story 2

No new implementation — T013's "reset only if the license number actually changed" conditional (Phase 2) already implements this story's entire behavior. This phase is validation-only, proving the boundary T013 already establishes.

**Checkpoint**: Both user stories functional together.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T021 [P] Run all `quickstart.md` scenarios end-to-end against a running backend + frontend — **not done**; same Docker root cause as T004–T011/T018–T020 above. What could be verified without Docker was: `StaffOnboardingContractTest` (web-layer only) still passing, full backend `compileJava`/`compileTestJava` clean, full frontend suite (41/41) + `tsc --noEmit` clean
- [X] T022 [P] Add structured logging for the edit action (profile id + before/after `licenseVerified` boolean only — never log the license number value itself, matching `StaffOnboardingService`'s existing discipline)
- [X] T023 Security review pass: confirm the `PATCH` endpoint sits behind `SuperAdminSecurityConfig`'s existing matcher with no gap, and that `edit()` contains no call into any booking/cascade/notification code path (FR-006) — verified: `DoctorVerificationController` adds no new `@RequestMapping` base path (still `/api/v1/admin/doctors`, already covered); `DoctorVerificationService.edit()` calls only `DoctorProfileRepository` methods, no event publisher, no reference to any booking/notification module

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — BLOCKS both user stories.
- **User Story 1 (Phase 2)**: Depends on Foundational. Tests (T004–T012) before implementation (T013–T017).
- **User Story 2 (Phase 3)**: Depends on Foundational AND on US1's `edit()` implementation (T013) existing — it validates behavior T013 already provides, rather than adding new code.
- **Polish (Final Phase)**: Depends on both user stories.

### Parallel Opportunities

- T002, T003 (Foundational) run in parallel; T001 is independent of both.
- All nine US1 test tasks (T004–T012) run in parallel.
- Backend (T013–T015) and frontend (T016–T017) US1 implementation tracks are independent and can proceed in parallel once Foundational is done.
- All three US2 test tasks (T018–T020) run in parallel.

---

## Implementation Strategy

### MVP First — User Story 1

1. Complete Phase 1: Foundational.
2. Complete Phase 2: User Story 1 — tests first (T004–T012), then implementation (T013–T017).
3. **STOP and VALIDATE** US1 independently (license-number edits correctly reset verification; other-field edits not yet explicitly proven, though already correctly handled by the same conditional).
4. Layer in User Story 2 (T018–T020) — pure validation of the existing boundary.
5. Polish (T021–T023).

### Notes

- Verify each test in T004–T012 and T018–T020 actually fails before writing its corresponding implementation (Constitution Principle I) — for US2's tests specifically, this means confirming they'd fail against a naive "always reset on any edit" implementation, not just that they pass once T013 is correctly written.
- T007 is the test proving FR-007/Constitution Principle IV's data-layer race guarantee for edited license numbers — reuses the same `uq_doctor_profile_license_number` constraint 007 added, don't shortcut to an app-level-only check.
- T013 is the one task where correctness matters most: get the "did the license number actually change" comparison and the "no event published" requirement (FR-006) right, and every other task in this feature follows directly from it.
