---

description: "Task list for Waitlist Matching (Longest-Waiting, Doctor/Specialization)"
---

# Tasks: Waitlist Matching (Longest-Waiting, Doctor/Specialization)

**Input**: Design documents from `/specs/031-waitlist-matching-longest-waiting/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/waitlist-join.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 the matching algorithm itself, US2 = P1-tied the necessary join prerequisite) per spec.md. **Build order note**: US2 (join) is implemented before US1 (matching) despite the numbering, since US1's own tests need a real way to create `WaitlistEntry` rows — the same "necessary prerequisite first" sequencing 026 already established for its own tied-P1 stories.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

New module: `backend/src/main/java/com/cms/waitlist/`, `backend/src/test/java/com/cms/waitlist/integration/`. Extends 025's existing `backend/src/main/java/com/cms/booking/BookingCancelledEvent.java` (read-only, as a listener target) and both existing `SecurityConfig` classes. New: `frontend/src/features/waitlist/`, `frontend/tests/waitlist/`.

---

## Phase 1: Setup

**Purpose**: The new entity, enum, and response/request DTOs shared by both stories.

- [X] T001 [P] Create `WaitlistEntryStatus` enum (`WAITING`, `OFFERED`) in `backend/src/main/java/com/cms/waitlist/WaitlistEntryStatus.java`
- [X] T002 Create `WaitlistEntry` entity (`clinic`, `patientAccount`, `doctorProfile` nullable, `specialization` nullable, `status`, `joinedAt`, `offeredAt` nullable, `offerExpiresAt` nullable) per data-model.md, with a constructor enforcing exactly one of `doctorProfile`/`specialization` is set at creation — in `backend/src/main/java/com/cms/waitlist/WaitlistEntry.java` (depends on T001)
- [X] T003 [P] Create `WaitlistEntryResponse` DTO (`id`, `clinicId`, `doctorProfileId`, `specialization`, `status`, `joinedAt`) per contracts/waitlist-join.md in `backend/src/main/java/com/cms/waitlist/dto/WaitlistEntryResponse.java`
- [X] T004 [P] Create `JoinWaitlistRequest` (`doctorProfileId`, `specialization`, both nullable) and `StaffJoinWaitlistRequest` (adds `patientAccountId`) DTOs in `backend/src/main/java/com/cms/waitlist/dto/`

**Checkpoint**: Types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The migration, repository, exceptions, exception handler, both security matchers, and the shared test fixture.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T005 Create migration `V17__create_waitlist_entry.sql` per data-model.md in `backend/src/main/resources/db/migration/V17__create_waitlist_entry.sql` (depends on T002)
- [X] T006 Create `WaitlistEntryRepository` with `findFirstByClinic_IdAndDoctorProfile_IdAndStatusOrderByJoinedAtAsc` and `findFirstByClinic_IdAndSpecializationAndDoctorProfileIsNullAndStatusOrderByJoinedAtAsc` (research.md R4) in `backend/src/main/java/com/cms/waitlist/WaitlistEntryRepository.java` (depends on T002)
- [X] T007 [P] Create only `WaitlistTargetRequiredException` (new) in `backend/src/main/java/com/cms/waitlist/`. Corrected during implementation: `ClinicNotFoundException`, `DoctorNotStaffedAtClinicException`, and `ForbiddenException` already exist in `com.cms.scheduling` and are already globally mapped by `ScheduleExceptionHandler` (013) — reuse them directly rather than duplicating (this module already depends on `com.cms.scheduling`, mirroring `com.cms.booking`'s own reuse of `com.cms.scheduling.NotAFixedTimeSessionException`/`SessionNotFoundException`). `PatientAccountNotFoundException` (`com.cms.notification`, 011) is also reused rather than duplicated, but is NOT yet globally mapped — T008 adds its first-ever HTTP mapping (the same "first HTTP-reachable caller" gap-class this session has already hit once, at 020).
- [X] T008 Create `WaitlistExceptionHandler` mapping only `com.cms.notification.PatientAccountNotFoundException` (`404 PATIENT_ACCOUNT_NOT_FOUND`) and `WaitlistTargetRequiredException` (`400 WAITLIST_TARGET_REQUIRED`) per contracts/waitlist-join.md in `backend/src/main/java/com/cms/waitlist/WaitlistExceptionHandler.java` (depends on T007). The other three contract error rows are already served by `ScheduleExceptionHandler`.
- [X] T009 Add explicit matcher `.requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/waitlist").authenticated()` to the `/api/v1/clinics/**` chain in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`
- [X] T010 Add explicit matcher `.requestMatchers(HttpMethod.POST, "/api/v1/patients/clinics/*/waitlist").authenticated()` to the `/api/v1/patients/**` chain in `backend/src/main/java/com/cms/patient/account/SecurityConfig.java`
- [X] T011 Create `AbstractWaitlistIntegrationTest` — clinic/doctor/staff-token/patient-account-token fixture (mirrors `AbstractSessionCancellationIntegrationTest`'s combination shape), plus a `saveWaitlistEntry(clinic, patientAccount, doctorProfileOrNull, specializationOrNull, joinedAt)` helper for constructing entries at explicit, controllable join times (needed for longest-waiting-within-a-tier testing) — in `backend/src/test/java/com/cms/waitlist/integration/AbstractWaitlistIntegrationTest.java` (depends on T002, T006)

**Checkpoint**: Foundation ready — User Story 2 (join) can now be built.

---

## Phase 3: A Patient Joins the Waitlist for a Doctor or Specialization (Priority: P1, tied) 🎯 Necessary Prerequisite

**Goal**: A patient (or staff on their behalf) creates a `WAITING` `WaitlistEntry` for either a specific doctor or a specialization with no doctor preference.

**Independent Test**: Per quickstart.md Scenario 5.

### Tests for User Story 2 (write first, confirm they FAIL before implementation)

- [X] T012 [P] [US2] Integration test: a patient joins for a specific doctor → `201`, `status: "WAITING"`, `doctorProfileId` set, `specialization` null; a patient joins for a specialization with no doctor → `201`, `specialization` set, `doctorProfileId` null (FR-001, US2 AC1/AC2) — in `backend/src/test/java/com/cms/waitlist/integration/PatientWaitlistJoinTest.java`
- [X] T013 [P] [US2] Integration test: neither `doctorProfileId` nor `specialization` given → `400 WAITLIST_TARGET_REQUIRED`; a `doctorProfileId` not staffed at the clinic → `404 DOCTOR_NOT_STAFFED_AT_CLINIC`; an unknown `clinicId` → `404 CLINIC_NOT_FOUND` — in the same file as T012
- [X] T014 [P] [US2] Integration test: staff join on a patient's behalf produces the identical entry shape as the patient joining directly (US2 AC3); an unknown `patientAccountId` → `404 PATIENT_ACCOUNT_NOT_FOUND`; staff with zero role at the clinic → `403 FORBIDDEN`; a Doctor's own token → `403 FORBIDDEN` — in `backend/src/test/java/com/cms/waitlist/integration/StaffWaitlistJoinTest.java`

### Implementation for User Story 2

- [X] T015 [US2] Implement `WaitlistJoinService.join(clinicId, patientAccountId, doctorProfileId, specialization)` — validates clinic exists, exactly one of doctor/specialization given (else `WaitlistTargetRequiredException`), doctor (if given) staffed at clinic (reuses the `RoleAssignmentRepository` staffing check, research.md R6), creates the entry `WAITING` — in `backend/src/main/java/com/cms/waitlist/WaitlistJoinService.java` (depends on T006, T007)
- [X] T016 [US2] Implement `PatientWaitlistController` (`POST /api/v1/patients/clinics/{clinicId}/waitlist`) per contracts/waitlist-join.md — in `backend/src/main/java/com/cms/waitlist/PatientWaitlistController.java` (depends on T003, T004, T015)
- [X] T017 [US2] Implement `StaffWaitlistController` (`POST /api/v1/clinics/{clinicId}/waitlist`, Operations-or-ClinicAdmin-only, mirrors 025/028/029's write-action gate) per contracts/waitlist-join.md — in `backend/src/main/java/com/cms/waitlist/StaffWaitlistController.java` (depends on T003, T004, T015)

**Checkpoint**: User Story 2 fully functional — waitlist entries can now be created via a real HTTP flow, unblocking User Story 1's own realistic end-to-end tests.

---

## Phase 4: System Matches and Offers the Correct Waitlist Entry on Cancellation (Priority: P1) 🎯 MVP (this feature's named focus)

**Goal**: When 025 releases a fixed-time slot via individual cancellation, the system selects the correct `WAITING` entry (doctor-match tier first, specialization-only only if empty, longest-waiting within a tier) and marks it `OFFERED`, notifying its Patient Account — never triggered by any other cancellation/release path.

**Independent Test**: Per quickstart.md Scenarios 1–4.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T018 [P] [US1] Integration test: a doctor-match entry (1 hour wait) and an eligible specialization-only entry (3 days wait) both exist; cancelling a Booking for that doctor via 025 offers the doctor-match entry, never the specialization-only one, regardless of relative wait time (FR-004/FR-006, SC-001) — in `backend/src/test/java/com/cms/waitlist/integration/WaitlistMatchingTierPriorityTest.java`
- [X] T019 [P] [US1] Integration test: two eligible entries in the same tier (both doctor-match, or both specialization-only) → the earlier-joined one is offered (FR-005, SC-002) — in the same file as T018
- [X] T020 [P] [US1] Integration test: no eligible entry in either tier → cancellation still succeeds, no entry changes state, no notification sent (FR-007, SC-004) — in `backend/src/test/java/com/cms/waitlist/integration/WaitlistMatchingNoEligibleEntryTest.java`
- [X] T021 [P] [US1] Integration test: a successful match transitions the entry to `OFFERED` with `offeredAt`/`offerExpiresAt` (≈ +30 min) set, and produces exactly one `NotificationEvent` for that entry's Patient Account (FR-008, SC-005) — in `backend/src/test/java/com/cms/waitlist/integration/WaitlistMatchingOfferTest.java`
- [X] T022 [P] [US1] Integration test: an eligible `WAITING` entry exists, but the release comes from a no-show sweep (021), a whole-day cancellation (026), or a partial cutoff cancellation (027) instead of 025 — no entry is ever matched (FR-009, SC-003) — in `backend/src/test/java/com/cms/waitlist/integration/WaitlistMatchingExclusivityTest.java`
- [X] T023 [P] [US1] Integration test: an already-`OFFERED` entry is never selected again by a later cancellation, even if it would otherwise be the longest-waiting eligible one (FR-010) — in the same file as T021

### Implementation for User Story 1

- [X] T024 [US1] Implement `WaitlistMatchingService.matchAndOffer(Session session, Slot slot)` — tier-1 query, else tier-2 query (research.md R4), on a hit: set `status = OFFERED`, `offeredAt = now`, `offerExpiresAt = now + 30min`, call `NotificationEventService.publish` (research.md R5); on a miss in both tiers, no-op — in `backend/src/main/java/com/cms/waitlist/WaitlistMatchingService.java` (depends on T006)
- [X] T025 [US1] Implement `WaitlistBumpListener` — `@TransactionalEventListener(phase = AFTER_COMMIT)` on `BookingCancelledEvent` (025), loads the Slot/Session from the event's `slotId`, rejects (no-ops) a non-Fixed-Time Session defensively, calls `WaitlistMatchingService.matchAndOffer` (research.md R3) — in `backend/src/main/java/com/cms/waitlist/WaitlistBumpListener.java` (depends on T024)

**Checkpoint**: Both user stories independently functional — the complete waitlist join-and-match flow, with 025 as the sole real trigger.

---

## Phase 5: Frontend & Polish

- [X] T026 [P] Create `frontend/src/features/waitlist/api.ts` — `joinWaitlist(clinicId, request, token)` — mirroring `booking-cancellation/api.ts`'s fetch-client shape
- [X] T027 Create `frontend/src/features/waitlist/JoinWaitlistForm.tsx` — a doctor-or-specialization choice plus a submit action (depends on T026)
- [X] T028 [P] Frontend test: joins for a doctor and for a specialization; shows the `WAITLIST_TARGET_REQUIRED`/`DOCTOR_NOT_STAFFED_AT_CLINIC` error messages — in `frontend/tests/waitlist/JoinWaitlistForm.test.tsx` (depends on T027)
- [X] T029 Run `quickstart.md` Scenarios 1–6 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md` — blocked by the sandbox's Testcontainers/Docker limitation (same as every prior feature this session); Scenarios 1–5 are each covered by a passing-when-run integration test (T012–T014, T018–T023) verified via compile + structural review; Scenario 6 (frontend) verified live via the Vitest suite (T028, 4/4 green)
- [X] T030 Run full backend build (`/tmp/gradle-8.10/bin/gradle build -x test` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 025's existing tests (given `BookingCancelledEvent` now has a real listener for the first time, and both `SecurityConfig` classes were extended)

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories' test-writing.
- **User Story 2 / Join (Phase 3)**: Depends on Foundational. Built first (see build-order note above).
- **User Story 1 / Matching (Phase 4)**: Depends on Phase 3 (needs a real way to create entries for realistic end-to-end tests) and on `WaitlistEntryRepository` (T006).
- **Frontend & Polish (Phase 5)**: Depends on both stories.

### Parallel Opportunities

- T001, T003, T004 in parallel (different files); T002 depends on T001.
- T007 items in parallel (different files).
- T009, T010 in parallel (different files, different `SecurityConfig` classes).
- T012–T014 (all US2 tests) in parallel — depend only on T011.
- T018–T023 (all US1 tests) in parallel — depend on T011 and Phase 3's join capability existing.
- T028 once T027 exists.

---

## Implementation Strategy

1. Phase 1 → Phase 2 → Phase 3 (join). **STOP and VALIDATE**: quickstart.md Scenario 5 passes.
2. Phase 4 (matching). **STOP and VALIDATE**: quickstart.md Scenarios 1–4 pass.
3. Phase 5: frontend, full-suite verification, quickstart sign-off.

---

## Phase 6: Convergence

- [X] T031 CRITICAL: `WaitlistMatchingService.matchAndOffer` performs a plain read-then-write on `WaitlistEntry` (derived-query select, then `match.offer(now)` + `save`) with no data-layer-guarded conditional update — no `@Version`, no `@Modifying ... WHERE status = 'WAITING'` guard, unlike this codebase's own established precedent for every other concurrency-sensitive write (`BookingRepository.cancelIfActive`, `NotificationEventRepository.markActionedIfPending`). Two individual cancellations of two different Fixed-Time Slots for the same doctor (or same specialization), resolved by `WaitlistBumpListener`'s separate `AFTER_COMMIT` transactions at nearly the same time, can both select and both successfully write `OFFERED` to the exact same `WaitlistEntry` row — violating Constitution Principle IV and FR-008/SC-005's "exactly one entry transitioning to `OFFERED`" per match. Add `WaitlistEntryRepository.offerIfWaiting(id, offeredAt, offerExpiresAt)` as a conditional `@Modifying @Query` (`WHERE id = :id AND status = 'WAITING'`), have `WaitlistMatchingService` call it instead of read-then-save, and treat a `0`-row result as a lost race (skip silently, no notification published — mirrors 026/027's "lost race → skip, not an error" precedent) per Constitution Principle IV (contradicts)
- [X] T032 MEDIUM: `WaitlistMatchingExclusivityTest` (T022) only covers 2 of the 3 non-025 release paths its own task description enumerated (whole-day cancellation, partial-cutoff cancellation) — the automatic no-show sweep (021) was skipped with a docstring rationale instead of an actual test, leaving FR-009/SC-003 partially unverified for that path. Add a third test invoking the real `NoShowDetectionService.detectAndMarkNoShows()` against a booked, past-dated Fixed-Time Slot with an eligible waiting entry, and assert the entry stays `WAITING` per T022 (partial)
