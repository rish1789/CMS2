---

description: "Task list for Doctor Profile Auto-Creation & License Verification Queue"
---

# Tasks: Doctor Profile Auto-Creation & License Verification Queue

**Input**: Design documents from `/specs/007-doctor-profile-license-queue/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/doctor-verification.md, contracts/staff-onboarding-extension.md, quickstart.md

**Tests**: Included and REQUIRED per Constitution Principle I.

**Organization**: Three user stories — US1 (P1, Super Admin verification queue, mirrors 003's clinic-verification pattern), US2 (P2, onboarding-time dedup/reuse extending 004's `StaffOnboardingService`), US3 (P3, discovery-eligibility data-layer guarantee, consumed later by 035). Reuses 003's `SuperAdminSecurityConfig`/`AdminExceptionHandler` pattern and 004's `DoctorProfile`/`DoctorProfileRepository`/`StaffOnboardingService` directly — no duplication.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

- [X] T001 [P] Create `frontend/src/features/doctor-verification/` directory scaffold

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 Create Flyway migration `V4__doctor_profile_visibility_and_license_uniqueness.sql` — adds `visible BOOLEAN NOT NULL DEFAULT TRUE` and `CONSTRAINT uq_doctor_profile_license_number UNIQUE (license_number)` to `doctor_profile` (data-model.md) in `backend/src/main/resources/db/migration/`
- [X] T003 [P] Extend `DoctorProfile` entity — add `visible` field (defaults `true`) with `isVisible()`/`setVisible(boolean)`, and `setLicenseVerified(boolean)` mutator (mirrors `Clinic.setVerified`) in `backend/src/main/java/com/cms/identity/doctor/DoctorProfile.java` (depends on T002)
- [X] T004 [P] Extend `DoctorProfileRepository` — add `Optional<DoctorProfile> findByLicenseNumber(String)` (US2's dedup lookup) and `List<DoctorProfile> findByLicenseVerified(boolean)` (US1's pending/verified list, mirrors `ClinicRepository.findByVerified`) in `backend/src/main/java/com/cms/identity/doctor/DoctorProfileRepository.java` (depends on T002)

**Checkpoint**: Foundation ready.

---

## Phase 3: User Story 1 - Super Admin Reviews the License-Verification Queue (Priority: P1) 🎯 MVP

**Goal**: A Super Admin lists Doctor Profiles awaiting license verification and marks one verified.

**Independent Test**: Onboard a Doctor (004), confirm they appear in `GET /api/v1/admin/doctors?verified=false` with their profile details, `POST .../verify`, confirm `licenseVerified` becomes `true` and they no longer appear in the pending list.

### Tests for User Story 1 ⚠️

> Write these tests FIRST; confirm they FAIL before starting implementation below.

- [ ] T005 [P] [US1] Integration test — `GET /api/v1/admin/doctors?verified=false` returns only unverified profiles with specialization/licenseNumber/experienceYears (FR-004); `?verified=true` returns only verified ones in `backend/src/test/java/com/cms/identity/admin/integration/PendingDoctorsListTest.java` — **written, compiles; unexecuted (Testcontainers/Docker sandbox limitation, same as 001–006)**
- [ ] T006 [P] [US1] Integration test — `POST /api/v1/admin/doctors/{id}/verify` flips `licenseVerified: false → true` (FR-005) in `backend/src/test/java/com/cms/identity/admin/integration/DoctorVerifyActionTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T007 [P] [US1] Integration test — a non-Super-Admin actor (ClinicAdmin, Doctor, Operations, or unauthenticated) is rejected `401` for both the list and verify endpoints, with zero state change (FR-006, SC-002) in `backend/src/test/java/com/cms/identity/admin/integration/DoctorVerificationAuthorizationTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T008 [P] [US1] Integration test — calling verify twice on the same profile succeeds idempotently both times with no duplicate side effects (FR-007, SC-005) in `backend/src/test/java/com/cms/identity/admin/integration/DoctorVerifyIdempotencyTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T009 [P] [US1] Integration test — verify on an unknown `doctorProfileId` returns `404`, no state change in `backend/src/test/java/com/cms/identity/admin/integration/DoctorVerifyNotFoundTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [X] T010 [P] [US1] Frontend test — `PendingDoctorsList` renders pending doctors, verify action removes one from the pending list on success in `frontend/tests/doctor-verification/PendingDoctorsList.test.tsx` — verified passing (5/5 new, 40/40 total)

### Implementation for User Story 1

- [X] T011 [US1] Implement `DoctorProfileNotFoundException` in `backend/src/main/java/com/cms/identity/admin/DoctorProfileNotFoundException.java`
- [X] T012 [US1] Implement `DoctorVerificationService` — `listByVerified(boolean)`, idempotent `verify(UUID)` (mirrors `ClinicVerificationService`) in `backend/src/main/java/com/cms/identity/admin/DoctorVerificationService.java` (depends on T004, T011)
- [X] T013 [P] [US1] Implement `DoctorProfileListResponse`, `DoctorProfileSummaryResponse`, `DoctorVerificationStatusResponse` DTOs per `contracts/doctor-verification.md` in `backend/src/main/java/com/cms/identity/admin/dto/`
- [X] T014 [US1] Implement `DoctorVerificationController` — `GET /api/v1/admin/doctors`, `POST /api/v1/admin/doctors/{doctorProfileId}/verify`, sitting behind the existing `SuperAdminSecurityConfig` matcher (no security-config change needed) in `backend/src/main/java/com/cms/identity/admin/DoctorVerificationController.java` (depends on T012, T013)
- [X] T015 [US1] Extend `AdminExceptionHandler` with a `DoctorProfileNotFoundException → 404` mapping, alongside the existing `ClinicNotFoundException` mapping (depends on T011, T014)
- [X] T016 [P] [US1] Implement `PendingDoctorsList` React component — single list view with Pending/Verified tabs and a Super Admin login gate, mirroring `PendingClinicsList`'s structure exactly (research.md) in `frontend/src/features/doctor-verification/PendingDoctorsList.tsx`
- [X] T017 [US1] Implement doctor-verification API client (`listDoctors`, `verifyDoctor`; HTTP Basic Auth credentials passed per call, mirrors `clinic-verification/api.ts`) in `frontend/src/features/doctor-verification/api.ts` (depends on T016)

**Checkpoint**: User Story 1 is fully functional and independently testable.

---

## Phase 4: User Story 2 - Doctor Profile Is Created Automatically at Onboarding (Priority: P2)

**Goal**: Extend 004's onboarding to dedup a Doctor by license number across clinics — reuse the existing global Account/Doctor Profile instead of creating a duplicate, reject a specialization mismatch, and never reset `licenseVerified` or issue new credentials on reuse.

**Independent Test**: Onboard the same license number + matching specialization at a second clinic; confirm no second Account/Doctor Profile is created, only a new Role Assignment, with the existing staff code returned and no new password. Repeat with a mismatched specialization and confirm rejection.

### Tests for User Story 2 ⚠️

- [ ] T018 [P] [US2] Integration test — Doctor onboarding with a license number matching no existing profile: `201`, `existingAccount=false`, new Account + DoctorProfile (`licenseVerified=false`, `visible=true`) + RoleAssignment, new credentials returned (unchanged behavior from 004) in `backend/src/test/java/com/cms/identity/staff/integration/OnboardDoctorNewProfileTest.java` — **written, compiles; unexecuted (Testcontainers/Docker sandbox limitation, re-confirmed this session)**
- [ ] T019 [P] [US2] Integration test — Doctor onboarding at a second clinic with the same license number and matching specialization (including a case/whitespace-only variant, e.g. "ent" vs "ENT") returns `201`, `existingAccount=true`, `temporaryPassword=null`, the existing `staffCode`, and the existing `doctorProfileId` — DB shows exactly one Account and one DoctorProfile but two RoleAssignments (FR-002, FR-002b, SC-006) in `backend/src/test/java/com/cms/identity/staff/integration/OnboardDoctorReuseTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T020 [P] [US2] Integration test — Doctor onboarding with a license number matching an existing profile but a different specialization returns `409 SPECIALIZATION_MISMATCH`, with zero Account/DoctorProfile/RoleAssignment rows created (FR-002a, SC-007) in `backend/src/test/java/com/cms/identity/staff/integration/OnboardDoctorSpecializationMismatchTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T021 [P] [US2] Integration test — a reused Doctor Profile's `licenseVerified` value is unchanged by the reuse-branch call, whether it was `true` or `false` beforehand (FR-002c, SC-008) in `backend/src/test/java/com/cms/identity/staff/integration/OnboardDoctorLicenseCarryOverTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T022 [P] [US2] Integration test — two concurrent onboarding submissions for the same new license number: exactly one succeeds creating a profile, the other is rejected — the DB-level `uq_doctor_profile_license_number` constraint is what actually closes the race, not just the app-level pre-check (Constitution Principle IV, mirrors 004's own email-uniqueness race test) in `backend/src/test/java/com/cms/identity/staff/integration/OnboardDoctorLicenseRaceTest.java` — **written, compiles; unexecuted (same sandbox limitation)**

### Implementation for User Story 2

- [X] T023 [P] [US2] Implement `SpecializationMismatchException` in `backend/src/main/java/com/cms/identity/staff/SpecializationMismatchException.java`
- [X] T024 [P] [US2] Extend `OnboardStaffResponse` with `existingAccount: boolean` per `contracts/staff-onboarding-extension.md` in `backend/src/main/java/com/cms/identity/staff/dto/OnboardStaffResponse.java`
- [X] T025 [US2] Extend `StaffOnboardingService.onboard`'s Doctor branch — look up `findByLicenseNumber` before the email-uniqueness check; on a case-insensitive/trimmed specialization match, reuse the existing Account/DoctorProfile (create only a new RoleAssignment, skip credential generation, leave `licenseVerified`/`visible` untouched); on a mismatch, throw `SpecializationMismatchException` with zero writes; on no match, proceed exactly as 004's original flow (data-model.md) in `backend/src/main/java/com/cms/identity/staff/StaffOnboardingService.java` (depends on T004, T023, T024) — verified via `StaffOnboardingContractTest` (no Docker needed) still passing after the change
- [X] T026 [US2] Wire `StaffExceptionHandler` mapping for `SpecializationMismatchException → 409 SPECIALIZATION_MISMATCH` (depends on T023)
- [X] T027 [P] [US2] Update `OnboardStaffForm` to surface the reuse outcome — when `existingAccount=true`, show "doctor already has an account (staff code X), no new password issued" instead of the credential-display screen (depends on T024)
- [X] T028 [US2] Update the staff-onboarding API client's response type with `existingAccount` in `frontend/src/features/staff-onboarding/api.ts` (depends on T027) — verified via full frontend suite (40/40) + `tsc --noEmit` clean

**Checkpoint**: User Stories 1 AND 2 both work independently.

---

## Phase 5: User Story 3 - Discovery Is Gated by License Verification, Visibility, and Clinic Verification Together (Priority: P3)

**Goal**: A Doctor Profile is discovery-eligible only when `licenseVerified=true` AND `visible=true` AND the doctor holds an active RoleAssignment at a verified clinic — enforced at the data-query level.

**Independent Test**: Query discovery eligibility directly at the data layer for a profile with each condition independently false; confirm exclusion in each case, and inclusion only when all three hold.

### Tests for User Story 3 ⚠️

- [ ] T029 [P] [US3] Integration test — `licenseVerified=false` excludes a profile from `findDiscoveryEligible()` regardless of `visible` or the clinic's `verified` state (FR-008, SC-004) in `backend/src/test/java/com/cms/identity/doctor/integration/DiscoveryEligibilityLicenseGateTest.java` — **written, compiles; unexecuted (Testcontainers/Docker sandbox limitation, re-confirmed this session)**
- [ ] T030 [P] [US3] Integration test — `licenseVerified=true` but `visible=false` excludes a profile from `findDiscoveryEligible()` in `backend/src/test/java/com/cms/identity/doctor/integration/DiscoveryEligibilityVisibilityGateTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T031 [P] [US3] Integration test — `licenseVerified=true`, `visible=true`, but the doctor's only active RoleAssignment is at an unverified clinic excludes a profile from `findDiscoveryEligible()` in `backend/src/test/java/com/cms/identity/doctor/integration/DiscoveryEligibilityClinicGateTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T032 [P] [US3] Integration test — all four conditions true (license verified, visible, clinic verified, active Role Assignment) includes the profile in `findDiscoveryEligible()` in `backend/src/test/java/com/cms/identity/doctor/integration/DiscoveryEligibilityAllConditionsTest.java` — **written, compiles; unexecuted (same sandbox limitation)**
- [ ] T032a [P] [US3] Integration test — `licenseVerified=true`, `visible=true`, clinic `verified=true`, but the doctor's Role Assignment at that clinic is deactivated (007) excludes the profile from `findDiscoveryEligible()` (FR-008, SC-004) in `backend/src/test/java/com/cms/identity/doctor/integration/DiscoveryEligibilityActiveRoleAssignmentGateTest.java` — **written, compiles; unexecuted (same sandbox limitation)**

### Implementation for User Story 3

- [X] T033 [US3] Implement `DoctorProfileRepository.findDiscoveryEligible()` — a JPQL query joining `RoleAssignment`/`Clinic` to enforce `licenseVerified = true AND visible = true AND EXISTS(RoleAssignment WHERE active = true AND Clinic.verified = true)` (data-model.md, FR-008; the query itself is this feature's data-layer contribution, the public discovery endpoint is 035's) in `backend/src/main/java/com/cms/identity/doctor/DoctorProfileRepository.java` (depends on T003)

**Checkpoint**: All three user stories independently functional.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T034 [P] Run all `quickstart.md` scenarios end-to-end against a running backend + frontend — **not done**; same Docker root cause as T005–T009/T018–T022/T029–T032a above. What could be verified without Docker was: `StaffOnboardingContractTest` (web-layer only) passing, full backend `compileJava`/`compileTestJava` clean, full frontend suite (40/40) + `tsc --noEmit` clean
- [X] T035 [P] Add structured logging for the onboarding reuse branch (outcome only — matched/created/rejected — never log license numbers or credentials) and for verify actions, mirroring `StaffOnboardingService`'s existing logging discipline
- [X] T036 Security review pass: confirm no credential or full license-number value ever appears in a log line or exception message (Constitution Principle IV), and that `SuperAdminSecurityConfig`'s existing matcher genuinely covers the two new endpoints with no gap — verified: all `log.info`/`log.warn` calls in `StaffOnboardingService`/`DoctorVerificationService` log only IDs/enum values, never license numbers/passwords; `SuperAdminSecurityConfig`'s `/api/v1/admin/**` matcher covers `/api/v1/admin/doctors/**` with no config change needed

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all three user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational. Tests (T005–T010) before implementation (T011–T017).
- **User Story 2 (Phase 4)**: Depends on Foundational. Independent of US1 (different endpoints/files), though both touch `identity.doctor`. Tests (T018–T022) before implementation (T023–T028).
- **User Story 3 (Phase 5)**: Depends on Foundational (specifically T003's `visible` field). Independent of US1/US2's own logic, but its tests are most naturally written against profiles already exercised by US1/US2's fixtures. Tests (T029–T032) before implementation (T033).
- **Polish (Final Phase)**: Depends on all three user stories.

### Parallel Opportunities

- T003, T004 (Foundational) run in parallel.
- All six US1 test tasks (T005–T010) run in parallel.
- Backend (T011–T015) and frontend (T016–T017) US1 implementation tracks are independent and can proceed in parallel once Foundational is done.
- All five US2 test tasks (T018–T022) run in parallel.
- T023, T024 (US2 implementation) run in parallel; both are prerequisites for T025.
- All five US3 test tasks (T029–T032, T032a) run in parallel.
- US1, US2, and US3 implementation tracks can proceed in parallel by different developers once Foundational is done — they touch different files (`admin/*` vs `staff/StaffOnboardingService.java` vs `doctor/DoctorProfileRepository.java`'s discovery query).

---

## Implementation Strategy

### MVP First — User Story 1

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational.
3. Complete Phase 3: User Story 1 — tests first (T005–T010), then implementation (T011–T017).
4. **STOP and VALIDATE** US1 independently (Super Admin can review and verify queued doctors; onboarding still creates a fresh profile per submission, no dedup yet).
5. Layer in User Story 2 (T018–T028) — onboarding-time dedup/reuse.
6. Layer in User Story 3 (T029–T033) — discovery-eligibility data-layer guarantee.
7. Polish (T034–T036).

### Notes

- Verify each test in T005–T010, T018–T022, and T029–T032 actually fails before writing its corresponding implementation (Constitution Principle I).
- T022 is the test proving FR-002/Constitution Principle IV's data-layer race guarantee for license-number dedup — don't shortcut it to an app-level-only check, mirroring how 004's T011 proved the same for email uniqueness.
- T025 is the single highest-risk implementation task — it changes control flow in an already-converged, tested method (`StaffOnboardingService.onboard`). Run 004's existing onboarding tests after T025 to confirm no regression to the non-Doctor and new-license-number paths, in addition to T018's own coverage.
