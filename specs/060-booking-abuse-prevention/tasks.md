---

description: "Task list for Booking Protection / Appointment Abuse Prevention"
---

# Tasks: Booking Protection / Appointment Abuse Prevention

**Input**: Design documents from `/specs/060-booking-abuse-prevention/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/booking-protection.md, quickstart.md

**Tests**: Included per this project's constitution (Principle I, Test-First Development, NON-NEGOTIABLE). Integration/concurrency tests are written/compiled per this project's standing convention even though they can't execute in this sandbox (Testcontainers/Docker limitation).

**Organization**: Tasks are grouped by user story (US1–US5, matching spec.md's priorities) so each is independently implementable and testable. `BookingProtectionService` is shared by US1 and US2 (both are preconditions on the same two booking-creation entry points) — its skeleton and wiring are built once in Foundational, and each story fills in its own check method, matching the precedent this project already used for a shared endpoint in 059-patient-clinical-record-access. Each of the two audit-history tables (added when AUD-002/AUD-003 were sharpened to require full change history, not just latest-state — research.md Decision 9) is built alongside its own current-state table, in the same module and the same phase.

## Phase 1: Setup

No setup tasks. This feature adds zero new dependencies (plan.md Technical Context) and extends the existing `booking` module plus introduces one new module (`protection`) on the existing stack.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The new schema, entities, repositories, the settings-reading plumbing every check depends on, and the shared precondition wiring point in both booking-creation entry points. Must complete before any user story phase.

- [x] T001 [P] Migration `backend/src/main/resources/db/migration/V36__booking_protection_log_and_override.sql`: create `booking_attempt_log` (id, patient_account_id, clinic_id, attempted_at, outcome, booking_id nullable), `clinic_booking_limit_override` (id, clinic_id unique, max_active_appointments, updated_at, updated_by), and `clinic_booking_limit_override_change_log` (id, clinic_id, previous_max_active_appointments nullable, new_max_active_appointments nullable, changed_at, changed_by) per data-model.md, with an index on `(patient_account_id, attempted_at)` for the rate-limit window query.
- [x] T002 [P] Migration `backend/src/main/resources/db/migration/V37__protection_settings_and_flags.sql`: create `protection_setting` (id, name unique, value, updated_at, updated_by), `protection_setting_change_log` (id, setting_name, previous_value nullable, new_value, changed_at, changed_by), and `suspicious_activity_flag` (id, patient_account_id, clinic_id nullable, signal_type, reason, detected_at, status, resolved_at, resolved_by) per data-model.md, including the partial unique index `UNIQUE (patient_account_id, clinic_id, signal_type) WHERE status = 'OUTSTANDING'` (data-model.md's dedup guarantee).
- [x] T003 [P] Create `BookingAttemptLog` entity in `backend/src/main/java/com/cms/booking/domain/BookingAttemptLog.java` (data-model.md fields, `outcome` as an enum: `SUCCESS`, `RATE_LIMITED`, `LIMIT_REACHED`, `OTHER_FAILURE`).
- [x] T004 [P] Create `ClinicBookingLimitOverride` entity in `backend/src/main/java/com/cms/booking/domain/ClinicBookingLimitOverride.java`.
- [x] T005 [P] Create `ClinicBookingLimitOverrideChangeLog` entity in `backend/src/main/java/com/cms/booking/domain/ClinicBookingLimitOverrideChangeLog.java` (append-only, data-model.md fields).
- [x] T006 [P] Create `ProtectionSetting` entity in `backend/src/main/java/com/cms/protection/domain/ProtectionSetting.java`.
- [x] T007 [P] Create `ProtectionSettingChangeLog` entity in `backend/src/main/java/com/cms/protection/domain/ProtectionSettingChangeLog.java` (append-only, data-model.md fields).
- [x] T008 [P] Create `SuspiciousActivityFlag` entity in `backend/src/main/java/com/cms/protection/domain/SuspiciousActivityFlag.java` (`signalType`/`status` as enums per data-model.md).
- [x] T009 [P] Create `BookingAttemptLogRepository` in `backend/src/main/java/com/cms/booking/repository/BookingAttemptLogRepository.java` with a windowed count query (`countByPatientAccountIdAndAttemptedAtAfter`) and a windowed-by-outcome count query (for the repeated-rate-limit-violations signal, FR-020). Depends on T003.
- [x] T010 [P] Create `ClinicBookingLimitOverrideRepository` in `backend/src/main/java/com/cms/booking/repository/ClinicBookingLimitOverrideRepository.java`. Depends on T004.
- [x] T011 [P] Create `ClinicBookingLimitOverrideChangeLogRepository` in `backend/src/main/java/com/cms/booking/repository/ClinicBookingLimitOverrideChangeLogRepository.java` with a `findByClinicIdOrderByChangedAtDesc` query. Depends on T005.
- [x] T012 [P] Create `ProtectionSettingRepository` in `backend/src/main/java/com/cms/protection/repository/ProtectionSettingRepository.java` with `findByName`. Depends on T006.
- [x] T013 [P] Create `ProtectionSettingChangeLogRepository` in `backend/src/main/java/com/cms/protection/repository/ProtectionSettingChangeLogRepository.java` with a `findBySettingNameOrderByChangedAtDesc` query. Depends on T007.
- [x] T014 [P] Create `SuspiciousActivityFlagRepository` in `backend/src/main/java/com/cms/protection/repository/SuspiciousActivityFlagRepository.java`, including a `findOutstandingByClinic`/`findOutstandingByPatientAndClinicAndSignalType`-style query for the dedup check and the clinic-scoped list endpoint. Depends on T008.
- [x] T015 Add active-appointment count methods to `backend/src/main/java/com/cms/booking/repository/BookingRepository.java`: a global count (`countByPatient_PatientAccount_IdAndStatus`) and a per-clinic count (join through `Patient.clinic`), both filtering `status = ACTIVE` only (spec.md FR-003).
- [x] T016 [P] Create `RateLimitedException` in `backend/src/main/java/com/cms/booking/exception/RateLimitedException.java` (carries a `retryAfterSeconds` value).
- [x] T017 [P] Create `BookingLimitReachedException` in `backend/src/main/java/com/cms/booking/exception/BookingLimitReachedException.java`.
- [x] T018 [P] Unit test `backend/src/test/java/com/cms/protection/unit/ProtectionSettingServiceTest.java`: returns the documented default (data-model.md's settings table) when no row exists for a name, returns the stored value once one exists, rejects a write to an unrecognized name, rejects a value that fails its setting's type/range validation, a successful write appends exactly one `ProtectionSettingChangeLog` row with the correct previous/new values (null previous on first-ever write).
- [x] T019 Implement `ProtectionSettingService` in `backend/src/main/java/com/cms/protection/service/ProtectionSettingService.java`: one typed read method per named setting (research.md Decision 5's fallback-to-default pattern) covering all 16 names in data-model.md, plus a generic Super-Admin write method that upserts `ProtectionSetting` and appends a `ProtectionSettingChangeLog` row in the same transaction. Depends on T012, T013, T018.
- [x] T020 Create `BookingProtectionService` skeleton in `backend/src/main/java/com/cms/booking/service/BookingProtectionService.java`: a single `checkAndRecordAttempt(UUID patientAccountId, UUID clinicId)` entry point that will call rate-limit-check then booking-limit-check (research.md Decision 6's fixed order) and always insert a `BookingAttemptLog` row — for this task, stub both check methods as no-ops (always pass) so the wiring below can be built and tested independently of either story's actual logic. Depends on T009, T015, T016, T017, T019.
- [x] T021 Wire `BookingProtectionService.checkAndRecordAttempt` into `backend/src/main/java/com/cms/booking/service/PatientBookingService.java`'s `bookSlot` as the very first line, before the existing slot lookup. Depends on T020.
- [x] T022 Wire the same call into `backend/src/main/java/com/cms/booking/service/PatientQueueBookingService.java`'s `bookSlot`. Depends on T020.
- [x] T023 Add explicit `.requestMatchers(HttpMethod.*, "/api/v1/clinics/*/protection/**")...authenticated()` entries to `backend/src/main/java/com/cms/identity/account/config/SecurityConfig.java` for the 7 new staff-realm protection paths (flags list/detail/resolve, limit-override GET/PUT/DELETE, limit-override/history GET) — this codebase has a documented, twice-real recurring bug where a new authenticated staff path is forgotten and falls through to `anyRequest().permitAll()`; add proactively now, before any of these controllers exist, matching this session's established discipline.

**Checkpoint**: schema, entities, repositories, and settings-reading plumbing (including audit-history writes) exist; both booking-creation entry points call into `BookingProtectionService` (currently a no-op gate); ready for user story implementation.

---

## Phase 3: User Story 1 - A patient is stopped from over-booking, and understands why (Priority: P1) 🎯 MVP

**Goal**: A patient at or over their active-appointment limit (global and/or per-clinic) is refused a new booking with a clear, non-accusatory message; cancelled bookings never count; the limit holds under concurrent attempts.

**Independent Test**: As a patient with active appointments at the configured limit, attempt one more booking anywhere and confirm refusal with a clear message, while a patient below the limit books normally (spec.md US1 Independent Test).

### Tests for User Story 1

- [x] T024 [P] [US1] Unit test `backend/src/test/java/com/cms/booking/unit/BookingProtectionServiceTest.java` — `checkBookingLimit`: rejects at/over the global limit, allows under it, a cancelled booking never counts, a per-clinic override is ANDed with the global limit (a patient under the global limit can still be refused at a clinic whose own override is stricter).
- [x] T025 [P] [US1] Contract test `backend/src/test/java/com/cms/booking/contract/PatientBookingLimitContractTest.java`: `409 BOOKING_LIMIT_REACHED` on both `POST .../slots/{slotId}/book` and `POST .../sessions/{sessionId}/queue-bookings` when the mocked service signals the limit is reached, `200` otherwise.
- [x] T026 [P] [US1] Integration test `backend/src/test/java/com/cms/booking/integration/BookingLimitConcurrencyTest.java`: two simultaneous booking requests for the same patient, already one below the limit, produce exactly one success and one `409` — never both succeeding (spec.md FR-006, NFR-003; mirrors `WalkInConcurrencyTest`/`QueueBookingConcurrencyTest`'s existing shape). Written/compiled, Docker-gated per this sandbox's standing limitation.
- [x] T027 [P] [US1] Frontend test `frontend/tests/patient-booking/BookingLimitError.test.tsx`: a `409 BOOKING_LIMIT_REACHED` response renders the clear, non-accusatory limit-reached message with no other-clinic detail.

### Implementation for User Story 1

- [x] T028 [US1] Implement `BookingProtectionService.checkBookingLimit` in `backend/src/main/java/com/cms/booking/service/BookingProtectionService.java`: read the global cap and per-clinic override via `ProtectionSettingService`/`ClinicBookingLimitOverrideRepository`, count active bookings via `BookingRepository` (T015), throw `BookingLimitReachedException` when either applicable cap would be exceeded, using a row lock on the patient's own account for the count-then-insert window (research.md Decision 1's concurrency guard). Depends on T024.
- [x] T029 [US1] Map `BookingLimitReachedException` to `409 BOOKING_LIMIT_REACHED` with the non-accusatory message text in the booking module's existing exception handler. Depends on T028.
- [x] T030 [US1] Frontend: catch `BOOKING_LIMIT_REACHED` in the existing patient booking-flow error handling (alongside the existing `SlotAlreadyBookedException`-style error surfacing) and render the clear message. Depends on T029.
- [x] T031 [US1] Live-verify via `quickstart.md` Scenario 1 against the running dev servers.

**Checkpoint**: User Story 1 fully functional and independently testable — a patient is correctly stopped from over-booking, with automated coverage and a live pass.

---

## Phase 4: User Story 2 - Repeated rapid booking attempts are throttled, not blocked forever (Priority: P1)

**Goal**: A patient attempting bookings faster than the configured threshold is refused with a clear temporary cooldown message; every attempt counts, whatever its outcome; the cooldown's end time is fixed once set.

**Independent Test**: As a patient, exceed the configured attempt threshold within the window, confirm refusal with a wait-time message, confirm a normal attempt succeeds again once the cooldown elapses (spec.md US2 Independent Test).

### Tests for User Story 2

- [x] T032 [P] [US2] Unit test `backend/src/test/java/com/cms/booking/unit/BookingProtectionServiceTest.java` — `checkRateLimit`: rejects once the attempt threshold is exceeded within the window, counts failed attempts (slot-taken, limit-reached) the same as successes, a further attempt during an active cooldown does not extend the cooldown's end time (Clarifications), and is evaluated before `checkBookingLimit` (Clarifications Q1 — a patient in cooldown always sees the cooldown outcome).
- [x] T033 [P] [US2] Contract test `backend/src/test/java/com/cms/booking/contract/PatientBookingRateLimitContractTest.java`: `429 RATE_LIMITED` with a `retryAfterSeconds` value on both booking endpoints when the mocked service signals cooldown.
- [x] T034 [P] [US2] Integration test `backend/src/test/java/com/cms/booking/integration/BookingRateLimitConcurrencyTest.java`: a burst of simultaneous attempts from the same patient around the threshold boundary never exceeds it by more than the bounded race margin documented in research.md Decision 1. Written/compiled, Docker-gated.
- [x] T035 [P] [US2] Frontend test `frontend/tests/patient-booking/RateLimitError.test.tsx`: a `429 RATE_LIMITED` response renders the cooldown message including the approximate wait time.

### Implementation for User Story 2

- [x] T036 [US2] Implement `BookingProtectionService.checkRateLimit` in `backend/src/main/java/com/cms/booking/service/BookingProtectionService.java`: windowed count via `BookingAttemptLogRepository` (T009), threshold/window/cooldown from `ProtectionSettingService`, fixed-cooldown-end-time semantics (store the cooldown's trigger timestamp, not a recomputed one, on each subsequent check), throw `RateLimitedException`. Depends on T032.
- [x] T037 [US2] Complete attempt-outcome recording in `PatientBookingService.bookSlot` and `PatientQueueBookingService.bookSlot`: every existing failure path below the new preconditions (slot already booked, slot in the past, fee resolution failure) and the success path each record their own `BookingAttemptLog` outcome (`OTHER_FAILURE`/`SUCCESS`) exactly once per attempt (spec.md FR-008). Depends on T036, T021, T022.
- [x] T038 [US2] Map `RateLimitedException` to `429 RATE_LIMITED` with `retryAfterSeconds` in the booking module's exception handler. Depends on T036.
- [x] T039 [US2] Frontend: catch `RATE_LIMITED` in the existing patient booking-flow error handling, render the cooldown message with wait time. Depends on T038.
- [x] T040 [US2] Live-verify via `quickstart.md` Scenario 2.

**Checkpoint**: User Stories 1 and 2 both independently functional — nothing from US1 was removed or changed.

---

## Phase 5: User Story 3 - Clinic staff review and resolve flagged suspicious activity (Priority: P2)

**Goal**: The five suspicion signals are detected on a schedule, surfaced as clinic-scoped flags with supporting evidence, and a ClinicAdmin can review and resolve them — without any flag ever restricting the patient.

**Independent Test**: Generate flag-worthy activity for a test patient at a clinic, confirm a flag appears with the correct reason and evidence, mark it resolved, confirm it drops out of the outstanding list (spec.md US3 Independent Test).

### Tests for User Story 3

- [x] T041 [P] [US3] Unit test `backend/src/test/java/com/cms/protection/unit/FlagDetectionServiceTest.java`: each of the 5 signals (FR-016–FR-020) fires exactly at its configured threshold and not before, using `ProtectionSettingService`'s thresholds; a signal that already has an outstanding flag for the same `(patient, clinic, signalType)` does not create a duplicate (Clarifications dedup rule); a resolved flag's signal is free to fire again.
- [x] T042 [P] [US3] Unit test (same file or `backend/src/test/java/com/cms/protection/unit/SuspiciousActivityFlagResolutionTest.java`): resolving an outstanding flag sets `status=RESOLVED` and `resolvedAt`/`resolvedBy` together, an already-resolved flag cannot be resolved again.
- [x] T043 [P] [US3] Contract test `backend/src/test/java/com/cms/protection/contract/ClinicProtectionFlagControllerContractTest.java`: list (filter by `status`/`patientAccountId`), detail (includes clinic-scoped `recentActivity` and the `atGlobalLimit` fact), resolve, `403` for a caller without ClinicAdmin at that clinic, `404` for a flag belonging to a different clinic.
- [x] T044 [P] [US3] Integration test `backend/src/test/java/com/cms/protection/integration/ProtectionFlagTenantIsolationTest.java`: a flag and its evidence created for Clinic A's activity are never visible to a ClinicAdmin at Clinic B, except the bare `atGlobalLimit` fact (spec.md FR-022, BR-004). Written/compiled, Docker-gated.
- [x] T045 [P] [US3] Frontend test `frontend/tests/clinic-protection/ProtectionFlagsList.test.tsx`: renders outstanding flags with reason/timestamp, resolving one removes it from the default outstanding view.

### Implementation for User Story 3

- [x] T046 [US3] Implement `FlagDetectionService` in `backend/src/main/java/com/cms/protection/service/FlagDetectionService.java`: one method per signal, reading `booking.BookingRepository`/`BookingAttemptLogRepository` and `scheduling.SlotRepository` (read-only, one-way per research.md Decision 3), writing via `SuspiciousActivityFlagRepository` with the dedup guard, run as a `@Scheduled` sweep (research.md Decision 4, mirroring `NoShowDetectionService`'s existing shape). Depends on T041.
- [x] T047 [US3] Implement the global-limit-fact computation (FR-022's cross-clinic exception) as a read-only query, not a stored flag row, in `backend/src/main/java/com/cms/protection/service/FlagDetectionService.java` or a small sibling service. Depends on T046.
- [x] T048 [US3] Create `ClinicProtectionFlagController` in `backend/src/main/java/com/cms/protection/api/ClinicProtectionFlagController.java`: list/detail/resolve endpoints per contracts/booking-protection.md, requiring ClinicAdmin specifically at `{clinicId}` (reusing `RoleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue`, research.md Decision 7). Depends on T043, T046.
- [x] T049 [US3] Implement the clinic-scoped `recentActivity` evidence assembly (recent bookings/cancellations/no-shows/rate-limit-violations, this clinic only) for the flag-detail endpoint. Depends on T048.
- [x] T050 [P] [US3] Create `frontend/src/features/clinic-protection/api.ts`: list/detail/resolve client calls.
- [x] T051 [US3] Create `frontend/src/features/clinic-protection/ProtectionFlagsList.tsx`: outstanding/resolved filter, search by patient, flag detail view with evidence, resolve action. Depends on T050.
- [x] T052 [US3] Wire a new "Booking Protection" route into the staff console (`frontend/src/routes/staff/...`, `frontend/src/App.tsx`), reachable from `ClinicToolsDashboard`, gated the same way every other ClinicAdmin-only screen already is. Depends on T051.
- [x] T053 [US3] Live-verify via `quickstart.md` Scenario 3.

**Checkpoint**: User Stories 1, 2, and 3 all independently functional.

---

## Phase 6: User Story 4 - A platform administrator configures the protection thresholds (Priority: P2)

**Goal**: A Super Admin can view and change every system-wide setting (including per-protection enable/disable toggles) at runtime, with changes taking effect on the next relevant request, and can review the full change history behind any setting's current value (AUD-002).

**Independent Test**: Change the global appointment limit, confirm it's enforced on the very next booking attempt with no deployment; turn a protection off and confirm it stops being enforced (spec.md US4 Independent Test).

### Tests for User Story 4

- [x] T054 [P] [US4] Contract test `backend/src/test/java/com/cms/protection/contract/SuperAdminProtectionSettingControllerContractTest.java`: `GET` lists every named setting with `isDefault` correctly reflecting whether a row exists, `PUT` updates a value and results in exactly one new `ProtectionSettingChangeLog` row (correct `previousValue`/`newValue`), `GET .../{name}/history` returns that change newest-first, `400` for an unrecognized name or an out-of-range value, `404` for `.../history` on an unrecognized name, `403` for a non-Super-Admin caller on any of the three endpoints.
- [x] T055 [P] [US4] Frontend test `frontend/tests/admin-protection-settings/ProtectionSettingsPage.test.tsx`: renders every setting, editing and saving one calls the update endpoint, expanding a setting's history calls the history endpoint and renders its entries.

### Implementation for User Story 4

- [x] T056 [US4] Create `SuperAdminProtectionSettingController` in `backend/src/main/java/com/cms/protection/api/SuperAdminProtectionSettingController.java`: `GET /api/v1/admin/protection-settings`, `PUT /api/v1/admin/protection-settings/{name}`, `GET /api/v1/admin/protection-settings/{name}/history` (reading via `ProtectionSettingChangeLogRepository`, T013), relying on `SuperAdminSecurityConfig`'s existing broad authenticated gate (research.md Decision 7 — no new matcher needed). Depends on T054, T019.
- [x] T057 [US4] Wire the `booking-limit.enabled` / `rate-limit.enabled` / `flagging.enabled` toggles (FR-027) into `BookingProtectionService.checkBookingLimit`/`checkRateLimit` (short-circuit to "pass" when disabled) and `FlagDetectionService` (skip the sweep when disabled). Depends on T056, T028, T036, T046.
- [x] T058 [P] [US4] Create `frontend/src/features/admin-protection-settings/api.ts`: list/update/history client calls.
- [x] T059 [US4] Create `frontend/src/features/admin-protection-settings/ProtectionSettingsPage.tsx`: table of settings with inline edit, per-protection toggles, an expandable history view per setting (AUD-004 — audit records must be viewable by administrators, not just stored). Depends on T058.
- [x] T060 [US4] Wire a new route into the Super Admin console (`frontend/src/routes/admin/...`, `frontend/src/App.tsx`), alongside the existing `AdminSectionShell` sections. Depends on T059.
- [x] T061 [US4] Live-verify via `quickstart.md` Scenario 4.

**Checkpoint**: User Stories 1–4 all independently functional.

---

## Phase 7: User Story 5 - A clinic administrator adds a stricter local appointment limit (Priority: P3)

**Goal**: A ClinicAdmin can optionally set a per-clinic supplementary cap, no stricter than the global limit is loose, enforced only at that clinic, with its own full change history (AUD-003).

**Independent Test**: Set a per-clinic limit lower than the global limit, confirm a patient under the global limit but over the clinic's own limit is refused there while still able to book elsewhere (spec.md US5 Independent Test).

### Tests for User Story 5

- [x] T062 [P] [US5] Unit test (extends `BookingProtectionServiceTest.java` or a new `ClinicBookingLimitOverrideServiceTest.java`): a write attempting to set `maxActiveAppointments` above the current global cap is rejected (BR-005); a successful create/update/delete each append exactly one `ClinicBookingLimitOverrideChangeLog` row with the correct previous/new values (null previous on first-ever set, null new on delete).
- [x] T063 [P] [US5] Contract test `backend/src/test/java/com/cms/booking/contract/ClinicBookingLimitOverrideControllerContractTest.java`: `GET`/`PUT`/`DELETE` on `.../protection/limit-override`, `GET .../limit-override/history` returns changes newest-first, `400` over-global-cap, `403`/`404` for a caller without ClinicAdmin at that specific clinic on any of the four endpoints.
- [x] T064 [P] [US5] Frontend test `frontend/tests/clinic-protection/ClinicLimitOverrideForm.test.tsx`: renders current override (or "none set"), saving a value calls the update endpoint, an over-cap value shows a validation message before submit, a history view renders past changes.

### Implementation for User Story 5

- [x] T065 [US5] Create `ClinicBookingLimitOverrideController` in `backend/src/main/java/com/cms/booking/api/ClinicBookingLimitOverrideController.java`: `GET`/`PUT`/`DELETE` under `/api/v1/clinics/{clinicId}/protection/limit-override`, `GET .../limit-override/history` (reading via `ClinicBookingLimitOverrideChangeLogRepository`, T011), ClinicAdmin-only at that clinic, BR-005 validation against the current global cap (read via `ProtectionSettingService`), every `PUT`/`DELETE` appending a `ClinicBookingLimitOverrideChangeLog` row in the same transaction. Depends on T063, T010, T011.
- [x] T066 [P] [US5] Add the limit-override and history client calls to `frontend/src/features/clinic-protection/api.ts` (extends US3's file).
- [x] T067 [US5] Create `frontend/src/features/clinic-protection/ClinicLimitOverrideForm.tsx`, including a simple history view (AUD-004), wired into the same Booking Protection area US3 built. Depends on T066, T052.
- [x] T068 [US5] Live-verify via `quickstart.md` Scenario 5.

**Checkpoint**: All five user stories independently functional — the full feature as specified.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [x] T069 [P] `cd backend && ./gradlew spotlessApply test --tests "com.cms.booking.*" --tests "com.cms.protection.*"` — full unit + contract suite green (integration/concurrency tests compiled, unexecuted per this sandbox's standing Docker limitation).
- [x] T070 [P] `cd frontend && npx tsc -b` — zero type errors.
- [x] T071 [P] `cd frontend && npm run lint` — zero new warnings in any file this feature touches.
- [x] T072 `cd frontend && npm run test -- --run` — full suite green, zero regressions.
- [x] T073 Grep `backend/src/main/java/com/cms/identity/account/config/SecurityConfig.java` for all 7 new staff-realm protection paths and confirm each has an explicit `.authenticated()` matcher (T023) — this codebase's own recurring bug class, do not skip this check.
- [x] T074 Implement the `BookingAttemptLog` retention sweep (spec.md SEC-005 — rows older than the longest active window age out), as its own scheduled task alongside `FlagDetectionService`'s sweep, not a new mechanism. Confirm `ClinicBookingLimitOverrideChangeLog`/`ProtectionSettingChangeLog` are explicitly exempt from any retention/aging-out (data-model.md — audit history is kept indefinitely, unlike `BookingAttemptLog`).
- [x] T075 Regression check (`quickstart.md`'s Regression checks section): confirm the existing IP-only `RateLimitingFilter`-protected endpoints (staff login, patient login/signup, clinic registration) and their existing tests are untouched; grep confirms `FlagDetectionService` is the only new reader of `Slot.status = NO_SHOW` and that no slot-generation/buffer-sizing code path was reintroduced (Constitution IV, spec.md's confirmed-existing-behavior section); confirm `StaffBookingService`/`WalkInInsertionService` (the walk-in booking-creation path) were not touched by T021/T022's wiring and gained no new precondition — add or run an existing walk-in-creation test to prove it still succeeds unaffected (spec.md FR-032, BR-006).

---

## Phase 9: Post-release fix - rate-limit burst bypass (2026-09-30)

**Found by**: CI on `main` (run 36604543917) - `BookingRateLimitConcurrencyTest` admitted 12 of 12
simultaneous attempts against a threshold of 8. Cause: the attempt row was written *after* the
booking, inside its transaction, so every attempt in a burst counted the same pre-burst rows
(research.md Decision 1 says it is written *before*). The same placement meant a failed
fixed-time attempt's row rolled back with its booking, so failures never counted (FR-008).

- [x] T076 Test first: `BookingRateLimitConcurrencyTest` tightened from "at most threshold + 1" to
  exactly the threshold, on both booking paths (the queue path was untested), plus a new case
  proving a rolled-back fixed-time attempt still counts; four new `BookingProtectionServiceTest`
  cases (lock taken before the count, admitted row recorded up front, rejection not written
  inside the doomed transaction, `recordAfterRollback` outcome mapping). Fix:
  `BookingProtectionService.checkAndRecordAttempt` takes the patient row lock *before* the
  rate-limit count and inserts the admitted attempt's row (OTHER_FAILURE, flipped by
  `recordSuccess`) in the same transaction; a rejection or failure is recorded by
  `recordAfterRollback` once that transaction has ended. `PatientBookingService.bookSlot` now
  runs its gate and booking in a `TransactionTemplate` so that recording happens outside it.
  A first attempt that wrote the row via `REQUIRES_NEW` while holding the lock was rejected:
  with a burst larger than the connection pool (10) every request timed out waiting for a
  second connection. Verified with Docker: `com.cms.booking.*`, `com.cms.waitlist.*`,
  `com.cms.protection.*`, `com.cms.patient.*` - 476/477 green; the one failure,
  `SessionCancellationSuccessTest.fixedTimeSessionWithMixedSlotStatesCancelsOnlyTheActiveBookings`,
  writes its bookings directly (not through this code), is today-dated, and passed on rerun.

## Dependencies & Execution Order

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: T001–T023. Blocks every user story — the shared `BookingProtectionService` wiring (T020–T022) is what US1/US2 fill in; the settings plumbing (T018–T019) and both audit-history repositories (T011, T013) are what US4/US5 build their history endpoints on; the schema (T001–T002) underlies everything.
- **US1 (T024–T031)**: depends only on Foundational. Independent of US2/US3/US4/US5's own logic, though it shares `BookingProtectionService.java` and both `bookSlot` methods as files with US2 (sequential edits to the same files, not a hidden coupling of business logic — mirrors 057's and 059's own same-file-different-story precedent).
- **US2 (T032–T040)**: depends on Foundational; T037 additionally depends on Foundational's T021/T022 wiring already existing (same file).
- **US3 (T041–T053)**: depends on Foundational (specifically the schema and `BookingAttemptLogRepository`/`BookingRepository` T009/T015, which US1/US2 also use but doesn't require US1/US2's *own* checks to be implemented — `FlagDetectionService` reads the same tables independently).
- **US4 (T054–T061)**: depends on Foundational (T019, T013); T057 additionally depends on US1/US2/US3's check methods already existing (T028, T036, T046) since the toggles wrap them.
- **US5 (T062–T068)**: depends on Foundational (T010, T011) and, for its frontend task (T067), on US3's `ProtectionFlagsList`/route already existing (T052) — same "later story's UI builds on an earlier story's same-file/same-area edit" pattern as US2-on-US1 and 057/059's precedent.
- **Polish (Phase 8)**: depends on all five user stories being complete.

## Parallel Example: Foundational

```bash
# T001, T002 touch different migration files; T003-T008 touch different entity files;
# T016, T017 touch different exception files — all parallelizable once T001/T002 land:
Task: "Migration V36: booking_attempt_log + clinic_booking_limit_override + its change log"
Task: "Migration V37: protection_setting + its change log + suspicious_activity_flag"
Task: "Create BookingAttemptLog entity"
Task: "Create ClinicBookingLimitOverride entity"
Task: "Create ClinicBookingLimitOverrideChangeLog entity"
Task: "Create ProtectionSetting entity"
Task: "Create ProtectionSettingChangeLog entity"
Task: "Create SuspiciousActivityFlag entity"
```

## Parallel Example: User Story 1 Tests

```bash
# T024-T027 touch different files - parallelizable:
Task: "Unit test BookingProtectionService.checkBookingLimit"
Task: "Contract test PatientBookingLimitContractTest"
Task: "Integration test: booking limit concurrency"
Task: "Frontend test: BookingLimitError"
```

## Implementation Strategy

### MVP First (User Stories 1 + 2 — both P1)

1. Complete Phase 2: Foundational.
2. Complete Phase 3: User Story 1 — a patient is correctly stopped from over-booking. Deployable on its own (rate limiting still a no-op).
3. Complete Phase 4: User Story 2 — booking attempts are throttled. Together, US1+US2 are this feature's MVP: both synchronous protections live, nothing admin-facing yet.
4. **STOP and VALIDATE**: run `quickstart.md` Scenarios 1 and 2.

### Incremental Delivery

1. Foundational → US1 + US2 (MVP, both P1) → validate → demo.
2. US3 (admin flagging & review) → validate → demo.
3. US4 (Super Admin configuration) → validate → demo — note US1–US3 already work against hardcoded defaults before this lands; US4 makes them tunable without a deploy, and makes every past change reviewable.
4. US5 (per-clinic override) → validate → demo.
5. Polish once all five are in.

## Notes

- Total: 75 tasks (23 Foundational + 8 US1 + 9 US2 + 13 US3 + 8 US4 + 7 US5 + 7 Polish).
- Two new Flyway migrations (V36, V37) — no change to any existing migration (forward-only,
  Constitution's Technology & Platform Constraints).
- This feature introduces the `com.cms.protection` module's first code — all of its unit and
  contract tests (T018, T041, T042, T054) are that module's first executable test coverage in this
  sandbox, matching the pattern already established when 059-patient-clinical-record-access gave
  `clinical` its first unit/contract tiers.
- The two synchronous checks (`checkBookingLimit`, `checkRateLimit`) live inside `com.cms.booking`
  specifically to avoid a module dependency cycle with `com.cms.protection` (research.md Decision 3)
  — do not move them into `protection` during implementation without re-reading that decision.
- The two audit-history tables (`ClinicBookingLimitOverrideChangeLog`, `ProtectionSettingChangeLog`)
  each live in the same module as the current-state table they audit, for the same
  cycle-avoidance reason (research.md Decision 9) — do not consolidate them into one shared table
  without re-reading that decision.
- No existing migration, entity, or endpoint is modified beyond the additive changes listed above —
  T075 exists specifically to confirm that stayed true, mirroring 059's own T047 precedent.
