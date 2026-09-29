---

description: "Task list for Clinic Staff Console — Browse & Pick Instead of Type-an-ID"
---

# Tasks: Clinic Staff Console — Browse & Pick Instead of Type-an-ID

**Input**: Design documents from `/specs/041-staff-console-pickers/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/staff-console-pickers.md, quickstart.md

**Tests**: Included as first-class tasks — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1–US4, matching spec.md's priorities) after a shared Foundational phase.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Web app per plan.md: `backend/src/main/java/com/cms/...`, `backend/src/test/java/com/cms/...`, `frontend/src/...`, `frontend/tests/...`.

---

## Phase 1: Setup

None required — this feature adds no new dependency, module, or configuration key (research.md, plan.md Constitution Check).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The one piece of shared, blocking infrastructure every story's endpoint needs to be reachable at all.

- [X] T001 Add 6 new `GET` matchers to the existing `/api/v1/clinics/**` chain in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java` — `/clinics/mine`, `/clinics/*/sessions`, `/clinics/*/sessions/*/day-sheet`, `/clinics/*/doctors`, `/clinics/*/staff`, `/clinics/*/patients/search` — each `.authenticated()`, mirroring every prior addition to this chain exactly (contracts/staff-console-pickers.md)

**Checkpoint**: All 6 new endpoint paths are reachable by any valid staff JWT (still 404 until each story's controller lands) — user stories can now proceed.

---

## Phase 3: User Story 1 - Pick a clinic instead of typing its ID (Priority: P1) 🎯 MVP

**Goal**: Replace `StaffDashboard`'s "type a Clinic ID" form with a real list of the staff member's own clinics.

**Independent Test**: Log in as staff with roles at 2+ clinics; see both by name; click one; land in that clinic's workspace exactly as today's manual entry would.

### Tests for User Story 1 ⚠️ write first, confirm they fail

- [X] T002 [P] [US1] Integration test: `GET /api/v1/clinics/mine` returns only the caller's active-role clinics (name, address, role); an account with zero active roles gets an empty list — new `backend/src/test/java/com/cms/identity/account/integration/StaffClinicControllerTest.java`
- [X] T003 [P] [US1] Frontend test: `MyClinicsList` renders clinic names from the API, navigates to `/staff/clinics/{clinicId}` on click, and shows a clear empty state when the list is empty — new `frontend/tests/staff-clinics/MyClinicsList.test.tsx`

### Implementation for User Story 1

- [X] T004 [US1] Add `findByAccount_IdAndActiveTrue(UUID accountId): List<RoleAssignment>` to `backend/src/main/java/com/cms/identity/account/RoleAssignmentRepository.java`
- [X] T005 [P] [US1] Create `ClinicMembershipResponse` record in `backend/src/main/java/com/cms/identity/account/dto/ClinicMembershipResponse.java` (data-model.md)
- [X] T006 [US1] Create `StaffClinicController` (`GET /api/v1/clinics/mine`) in `backend/src/main/java/com/cms/identity/account/StaffClinicController.java` — resolves the caller's Account ID via `SecurityConfig.currentAccountId`, maps active `RoleAssignment`s (T004) to `ClinicMembershipResponse` (T005); no 403 case (contracts/staff-console-pickers.md)
- [X] T007 [P] [US1] Create `frontend/src/features/staff-clinics/api.ts` — `listMyClinics(token): Promise<ClinicMembership[]>`
- [X] T008 [US1] Create `frontend/src/features/staff-clinics/MyClinicsList.tsx` — fetches on mount using the stored staff token, renders loading/empty/list states (mirrors `InboxPage.tsx`/`PendingClinicsList.tsx` shape, research.md R8), each row links to `/staff/clinics/{clinicId}`
- [X] T009 [US1] Replace the body of `frontend/src/routes/staff/StaffDashboard.tsx` with `<MyClinicsList />`, removing the Clinic-ID text-entry form entirely

**Checkpoint**: A staff member can log in and reach their clinic workspace with zero typed IDs.

---

## Phase 4: User Story 2 - Browse a clinic's day sheet to find and act on a session, slot, or booking (Priority: P1)

**Goal**: Replace 8 of the 16 ID-typing tool cards with a session list → per-session slot/booking view that launches every existing action already carrying the right IDs.

**Independent Test**: Inside a clinic with a generated session, see it listed (doctor/date/mode); open it; see its slots with status and, where booked, the patient's name; launch "Cancel booking" directly from a booked row and land on the existing cancel route with the booking ID pre-filled.

### Tests for User Story 2 ⚠️ write first, confirm they fail

- [X] T010 [P] [US2] Integration test: `GET /api/v1/clinics/{clinicId}/sessions` returns only sessions within the default 14-day window, with doctor name/date/mode; 403 for a caller with no active role at that clinic — new `backend/src/test/java/com/cms/scheduling/integration/ClinicSessionListControllerTest.java`
- [X] T011 [P] [US2] Integration test: `GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet` returns every slot with status, `isBuffer`, and — only for a slot with an ACTIVE booking — `booking.patientName`; a cancelled booking's slot shows `booking: null`; 404 for a session not belonging to that clinic; 403 for no active role — new `backend/src/test/java/com/cms/booking/integration/SessionDaySheetControllerTest.java`
- [X] T012 [P] [US2] Frontend test: `DaySheet` renders sessions from the API with doctor/date/mode, navigates to the session detail route on click, shows an empty state for no sessions — new `frontend/tests/day-sheet/DaySheet.test.tsx`
- [X] T013 [P] [US2] Frontend test: `SessionSlotsView` renders each slot's status and patient name when booked, marks a buffer slot as "Reserved capacity" with no Book action, and each action link navigates to the correct existing route (e.g. `/staff/clinics/{clinicId}/bookings/{bookingId}/cancel`) — new `frontend/tests/day-sheet/SessionSlotsView.test.tsx`

### Implementation for User Story 2

- [X] T014 [P] [US2] Add `findByClinic_IdAndSessionDateBetween(UUID clinicId, LocalDate from, LocalDate to): List<Session>` to `backend/src/main/java/com/cms/scheduling/SessionRepository.java`
- [X] T015 [P] [US2] Create `SessionSummaryResponse` record in `backend/src/main/java/com/cms/scheduling/dto/SessionSummaryResponse.java` (data-model.md)
- [X] T016 [US2] Create `ClinicSessionListController` (`GET /api/v1/clinics/{clinicId}/sessions?from&to`, defaulting to today/today+14 per FR-008) in `backend/src/main/java/com/cms/scheduling/ClinicSessionListController.java` — reuses the existing `scheduling.ForbiddenException`/`ScheduleExceptionHandler` for the active-role check (research.md R1)
- [X] T017 [P] [US2] Add `findBySlot_Session_IdAndStatus(UUID sessionId, BookingStatus status): List<Booking>` to `backend/src/main/java/com/cms/booking/BookingRepository.java` (research.md R3, batch lookup avoiding N+1)
- [X] T018 [P] [US2] Create `SessionDaySheetResponse`/`SlotDetail`/`BookingDetail` records in `backend/src/main/java/com/cms/booking/dto/SessionDaySheetResponse.java` (data-model.md) — `SessionDaySheetResponse` includes `doctorProfileId` (needed by the frontend to build "Book"/"Book into queue" URLs)
- [X] T019 [US2] Create `SessionDaySheetController` (`GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet`) in `backend/src/main/java/com/cms/booking/SessionDaySheetController.java` — loads the Session (404 via the existing `scheduling.SessionNotFoundException`, already mapped in `BookingExceptionHandler`), checks the caller's active role at `clinicId` (existing `booking.ForbiddenException`), merges `SlotRepository.findBySession_Id` (scheduling) with T017's batch booking lookup into `SlotDetail[]` (research.md R1/R3)
- [X] T020 [P] [US2] Create `frontend/src/features/day-sheet/api.ts` — `listSessions(clinicId, token)`, `getDaySheet(clinicId, sessionId, token)`
- [X] T021 [US2] Create `frontend/src/features/day-sheet/DaySheet.tsx` — session list for a clinic, loading/empty/list states, each row links to `/staff/clinics/{clinicId}/day-sheet/{sessionId}`
- [X] T022 [US2] Create `frontend/src/features/day-sheet/SessionSlotsView.tsx` — renders each slot (time or token number, status, patient name if booked); a buffer slot shows "Reserved capacity" and no Book action (FR-005, R6); a session-level "Insert a walk-in" link; per-booked-slot action links to the existing routes (cancel, queue position, consultation note, prescription, external record — clinical-documentation links shown whenever a booking exists regardless of slot status, cancel/queue-position only while the slot is `BOOKED`, matching each destination route's own existing preconditions); an OPEN non-buffer slot on a Fixed-Time session links to the existing `/slots/{slotId}/book?doctorProfileId=` route, and a Queue-mode session offers the existing `/sessions/{sessionId}/queue-book?doctorProfileId=` route
- [X] T023 [US2] Add routes `/staff/clinics/:clinicId/day-sheet` (`DaySheet`) and `/staff/clinics/:clinicId/day-sheet/:sessionId` (`SessionSlotsView`) to `frontend/src/App.tsx`, inside the existing `RequireStaffSession`/`ClinicShell` nesting
- [X] T024 [US2] Modify `frontend/src/routes/staff/ClinicToolsDashboard.tsx` — remove the 8 tool cards now covered by the day sheet (Book a slot, Book into a queue, Insert a walk-in, Queue position, Cancel a booking, Session operations, Cancel from a cutoff, Cancel a whole session), replacing them with one prominent link into `/staff/clinics/{clinicId}/day-sheet`

**Checkpoint**: User Stories 1 and 2 together let a staff member go from login to acting on a specific booking with zero typed IDs (SC-001).

---

## Phase 5: User Story 3 - Pick a patient instead of typing their ID (Priority: P2)

**Goal**: Replace the "Anonymize a patient" tool's Patient ID box with a name/phone search.

**Independent Test**: Search a known patient by partial name; see them in results; select them; land on the existing anonymize route with the Patient ID pre-filled.

### Tests for User Story 3 ⚠️ write first, confirm they fail

- [X] T025 [P] [US3] Integration test: `GET /api/v1/clinics/{clinicId}/patients/search?q=` matches by partial name and by phone, clinic-scoped only; empty array (not an error) for no matches; `400` for `q` under 2 characters; **an already-anonymized patient (name literally `"Anonymized Patient"`) never appears, even when `q` matches that exact string** — new `backend/src/test/java/com/cms/patient/record/integration/ClinicPatientSearchControllerTest.java`
- [X] T026 [P] [US3] Frontend test: `PatientSearch` shows matching patients by name, shows a clear "no matches" state, and each result links to the existing anonymize route with the Patient ID filled in — new `frontend/tests/patient-search/PatientSearch.test.tsx`

### Implementation for User Story 3

- [X] T027 [US3] Add `search(UUID clinicId, String term): List<Patient>` to `backend/src/main/java/com/cms/patient/record/PatientRepository.java` (research.md R4, case-insensitive partial name OR phone match, clinic-scoped, **excludes `anonymizedAt IS NOT NULL` patients** — Analyze-stage F1 fix)
- [X] T028 [P] [US3] Create `PatientSearchResultResponse` record in `backend/src/main/java/com/cms/patient/record/dto/PatientSearchResultResponse.java` (data-model.md)
- [X] T029 [US3] Create `ClinicPatientSearchController` (`GET /api/v1/clinics/{clinicId}/patients/search?q=`) in `backend/src/main/java/com/cms/patient/record/ClinicPatientSearchController.java` — validates `q` length (400 if under 2 chars), reuses the existing `patient.record.ForbiddenException`/`PatientRecordExceptionHandler` for the active-role check
- [X] T030 [P] [US3] Create `frontend/src/features/patient-search/api.ts` — `searchPatients(clinicId, term, token)`
- [X] T031 [US3] Create `frontend/src/features/patient-search/PatientSearch.tsx` — a search input, debounced fetch, result list with loading/empty/no-matches states, each result linking to `/staff/clinics/{clinicId}/patients/{patientId}/anonymize`; add its route to `frontend/src/App.tsx` at `/staff/clinics/:clinicId/patients/search` and a link to it from `ClinicToolsDashboard.tsx` replacing the "Anonymize a patient" ID box

**Checkpoint**: Stories 1–3 together cover clinic entry, day-to-day booking operations, and patient lookup with zero typed IDs.

---

## Phase 6: User Story 4 - Pick a doctor or staff member instead of typing their ID (Priority: P3)

**Goal**: Replace the "Define a schedule"/"Manage appointment types" (Doctor Profile ID) and "Deactivate staff" (Account ID) tool cards with pickers.

**Independent Test**: At a clinic with 2+ staffed doctors, see both by name for "Define a schedule"; at a clinic with 2+ active staff, see both by name for "Deactivate staff".

### Tests for User Story 4 ⚠️ write first, confirm they fail

- [X] T032 [P] [US4] Integration test: `GET /api/v1/clinics/{clinicId}/doctors` returns doctors with an active Doctor role at that clinic (name, specialization), regardless of license-verification status; 403 for no active role at the clinic — new `backend/src/test/java/com/cms/identity/doctor/integration/ClinicDoctorControllerTest.java`
- [X] T033 [P] [US4] Integration test: `GET /api/v1/clinics/{clinicId}/staff` returns every active `RoleAssignment` at that clinic (any role) with name and role — new `backend/src/test/java/com/cms/identity/staff/integration/ClinicStaffControllerTest.java`
- [X] T034 [P] [US4] Frontend test: `DoctorPicker` renders doctors by name/specialization, with links to both the schedule and appointment-types routes — new `frontend/tests/doctor-picker/DoctorPicker.test.tsx`
- [X] T035 [P] [US4] Frontend test: `StaffPicker` renders active staff by name/role, each linking to the existing deactivate route — new `frontend/tests/staff-picker/StaffPicker.test.tsx`

### Implementation for User Story 4

- [X] T036 [P] [US4] Create `identity.doctor.NotStaffedAtClinicException` in `backend/src/main/java/com/cms/identity/doctor/NotStaffedAtClinicException.java` and `DoctorExceptionHandler` in `backend/src/main/java/com/cms/identity/doctor/DoctorExceptionHandler.java` (this module has no exception handler yet). **Naming note (Analyze/implement-time correction, research.md R9)**: named `NotStaffedAtClinicException`, not `ForbiddenException` — and the same new exception (not a reuse of an existing `ForbiddenException`) was also added to `scheduling`, `booking`, and `identity.staff`, each of which already had its own feature-specific `ForbiddenException` with an unrelated message. Reusing those would have repeated the exact bug `com.cms.patient.record.ForbiddenException`'s own Javadoc documents as already having happened and been fixed once.
- [X] T037 [US4] Add `findByClinicStaffed(UUID clinicId): List<DoctorProfile>` to `backend/src/main/java/com/cms/identity/doctor/DoctorProfileRepository.java` (research.md R5, mirrors `findDiscoveryEligible()`'s join shape, no verification/visibility filter)
- [X] T038 [P] [US4] Create `DoctorSummaryResponse` record in `backend/src/main/java/com/cms/identity/doctor/dto/DoctorSummaryResponse.java` (data-model.md)
- [X] T039 [US4] Create `ClinicDoctorController` (`GET /api/v1/clinics/{clinicId}/doctors`) in `backend/src/main/java/com/cms/identity/doctor/ClinicDoctorController.java`, using T036's new exception type for the active-role check
- [X] T040 [US4] Add `findByClinic_IdAndActiveTrue(UUID clinicId): List<RoleAssignment>` to `backend/src/main/java/com/cms/identity/account/RoleAssignmentRepository.java` (research.md R5)
- [X] T041 [P] [US4] Create `StaffSummaryResponse` record in `backend/src/main/java/com/cms/identity/staff/dto/StaffSummaryResponse.java` (data-model.md)
- [X] T042 [US4] Create `ClinicStaffController` (`GET /api/v1/clinics/{clinicId}/staff`) in `backend/src/main/java/com/cms/identity/staff/ClinicStaffController.java`, reusing the existing `identity.staff.ForbiddenException`/`StaffExceptionHandler`
- [X] T043 [P] [US4] Create `frontend/src/features/doctor-picker/api.ts` + `DoctorPicker.tsx` (list, loading/empty states, each row links to both `/staff/clinics/{clinicId}/doctors/{doctorProfileId}/schedule` and `/appointment-types`)
- [X] T044 [US4] Create `frontend/src/features/staff-picker/api.ts` + `StaffPicker.tsx` (list, loading/empty states, each row links to `/staff/clinics/{clinicId}/staff/{accountId}/deactivate`); add both new routes (`/staff/clinics/:clinicId/doctors`, `/staff/clinics/:clinicId/staff`) to `frontend/src/App.tsx`; update `ClinicToolsDashboard.tsx` to replace "Define a schedule"/"Manage appointment types"/"Deactivate staff"'s ID boxes with links into these two pickers

**Checkpoint**: All four user stories complete — 0 typed IDs remain anywhere in the staff console except "Onboard staff" and "Join a patient to the waitlist" (both already ID-free, untouched by this feature).

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T045 [P] Run `quickstart.md` Scenarios 1–5 end-to-end against a real running backend + frontend
- [X] T046 Full backend build (`compileJava`, `compileTestJava`, `spotlessCheck`) green
- [X] T047 Full frontend suite (`vitest run`, `tsc -b`, lint) green, zero regressions in the pre-existing suite

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 2)**: Depends on nothing — BLOCKS all 4 user stories (their endpoints are unreachable without T001).
- **User Story 1 (Phase 3)**: Depends on Foundational only.
- **User Story 2 (Phase 4)**: Depends on Foundational only. Independent of US1 (its own routes work even if `StaffDashboard` still has the old form), though naturally exercised together.
- **User Story 3 (Phase 5)**: Depends on Foundational only.
- **User Story 4 (Phase 6)**: Depends on Foundational only.
- **Polish (Phase 7)**: Depends on all four stories complete.

All four user stories touch disjoint backend modules and disjoint frontend feature folders — they can be implemented in parallel by different people, or sequentially in priority order (US1/US2 tied at P1 → US3 → US4). The one shared file (`ClinicToolsDashboard.tsx`) is edited once per story (T024, T031, T044) — do these edits in story-priority order to avoid overwriting each other's changes.

### Parallel Opportunities

- T002/T003 (US1 tests); T010–T013 (US2 tests, 4 distinct files); T025/T026 (US3 tests); T032–T035 (US4 tests) — all parallel within their story.
- Across stories: since each story's backend work lives in a different module and each story's frontend work lives in a different feature folder, entire stories can run in parallel except for the shared `ClinicToolsDashboard.tsx` edit (T024/T031/T044) and `App.tsx` route additions (T023/T031/T044) — sequence those three specifically, in any order, since each touches disjoint sections of the same two files.

---

## Parallel Example: Cross-Story (if staffed in parallel)

```bash
# Four people, four stories, after T001 lands:
Developer A: T002-T009 (US1 - identity.account + staff-clinics)
Developer B: T010-T024 (US2 - scheduling + booking + day-sheet)
Developer C: T025-T031 (US3 - patient.record + patient-search)
Developer D: T032-T044 (US4 - identity.doctor + identity.staff + 2 pickers)
# ClinicToolsDashboard.tsx and App.tsx edits (T024/T031/T044) merged in story-priority order.
```

---

## Phase 8: Doctor Self-Scoping Follow-Up (FR-011, post-convergence, user-requested)

**Goal**: A Doctor sees only their own sessions/day-sheets; ClinicAdmin/Operations unaffected. View-only, no write-action authorization changed.

- [X] Add `RoleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(accountId, clinicId): List<RoleAssignment>` — inspects exact roles held, not just existence
- [X] Add `DoctorProfileRepository.findByAccount_Id(accountId): Optional<DoctorProfile>` — resolves the caller's own DoctorProfile
- [X] Modify `ClinicSessionListController` — when caller's only role is Doctor, filter the session list to their own `doctorProfileId`
- [X] Modify `SessionDaySheetController` — when caller's only role is Doctor, return `404` (mirroring the existing clinic-mismatch 404, not 403) for a session belonging to a different doctor
- [X] Test: `ClinicSessionListControllerTest.aDoctorOnlyCallerSeesOnlyTheirOwnSessionsWhileClinicAdminSeesEveryDoctors`
- [X] Test: `SessionDaySheetControllerTest.aDoctorCanViewTheirOwnSessionsDaySheet` + `aDoctorGets404ForAnotherDoctorsSessionAtTheSameClinic`
- [X] Live-verified against a real backend + Postgres: two doctors staffed at one clinic, Doctor A's session list showed only their own 15 sessions while ClinicAdmin's showed all 30; a third, unrelated Doctor got `404` viewing Doctor B's session day-sheet

No frontend changes needed — `DaySheet.tsx`/`SessionSlotsView.tsx` already just render whatever the API returns.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Foundational (T001) → User Story 1 (T002–T009).
2. **STOP and VALIDATE**: log in, see real clinic names, click through — the single highest-friction screen in the whole console is fixed.

### Incremental Delivery

1. Foundational → US1 (clinic picker) → US2 (day sheet) — together these two P1 stories deliver SC-001 (login to acting on a booking, zero typed IDs).
2. US3 (patient search) — closes the one remaining direct-patient-action gap.
3. US4 (doctor/staff pickers) — closes the two remaining occasional-admin-action gaps, reaching 0 typed IDs anywhere in scope (SC-002).
