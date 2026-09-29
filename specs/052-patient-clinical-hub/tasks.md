---

description: "Task list for Patient Context & Clinical History Hub"
---

# Tasks: Patient Context & Clinical History Hub

**Input**: Design documents from `/specs/052-patient-clinical-hub/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: The new booking-history endpoint is genuinely new backend logic — Constitution Principle I (Test-First) applies: tests written before/alongside implementation, same discipline as 048/051's own backend additions. `PatientSearch.test.tsx` already exists and is the regression baseline for the new navigation link.

**Organization**: US1 (P1) builds the hub itself — backend booking-history endpoint + DTO extension, the `PatientHubPage` with all 5 sections, and the Patient Search entry point. This necessarily includes the Overview section showing identity/anonymization state, since Overview is one of the 5 required sections. US2 (P2) is a focused correctness pass specifically on anonymization display accuracy (a DPDP-compliance-sensitive guarantee warranting its own explicit test beyond US1's basic build). US3 (P2) is a focused audit-and-regression pass confirming no new edit capability exists and 030/031/032/033's existing authorization/immutability guarantees are unchanged.

## Phase 1-2: Setup / Foundational

Not needed — no shared infrastructure blocks either story.

---

## Phase 3: User Story 1 - One place to see a patient's history at this clinic (Priority: P1) 🎯 MVP

**Goal**: A patient hub page exists, reachable from Patient Search, showing Overview + Bookings + Consultations + Prescriptions + External Records for the current clinic only.

**Independent Test**: From Patient Search, open a patient with existing bookings and confirm the hub shows identity info and a real, correctly-attributed list of bookings at this clinic.

### Tests for User Story 1 (write first — Constitution Principle I)

> Backend integration tests can't execute in this sandbox (established Testcontainers/Docker limitation) — written and reviewed same as every prior backend feature, verified they compile.

- [x] T001 [P] [US1] Add `backend/src/test/java/com/cms/patient/record/unit/PatientBookingHistoryControllerTest.java` (Mockito, no Spring context): returns bookings for a patient confirmed to belong to `clinicId`; throws `PatientNotFoundException` for a patient belonging to a *different* clinic (proves clinic-scoping, SC-002) or a nonexistent id; throws `NotStaffedAtClinicException` for a caller with no active role at the clinic. 4 tests, all passing.
- [x] T002 [P] [US1] Add `backend/src/test/java/com/cms/patient/record/contract/PatientBookingHistoryControllerContractTest.java` (`@WebMvcTest`): `200` with the correct JSON shape and pagination fields on success; `404` for a cross-clinic/nonexistent patient id; `403` for no active role; `401` for a missing bearer token (matches contracts/patient-booking-history.md). **This test caught a real, pre-existing bug**: not just for this feature's new endpoint, but for the already-shipped `PatientDetailController` (`GET .../patients/{id}`) too — neither had a `SecurityConfig` matcher, both fell through to `anyRequest().permitAll()`. Fixed in `SecurityConfig.java` (see T005's note). 4 tests, all passing after the fix.

### Implementation for User Story 1

- [x] T003 [US1] Edit `backend/src/main/java/com/cms/booking/repository/BookingRepository.java`: add `findByPatient_IdOrderBySlot_Session_SessionDateDesc(UUID patientId, Pageable pageable)` (research.md Decision 2 — derived query, mirrors the existing `findByPatient_PatientAccount_IdOrderByCreatedAtDesc`).
- [x] T004 [US1] Create `backend/src/main/java/com/cms/patient/record/dto/PatientBookingSummaryResponse.java` and `PatientBookingHistoryResponse.java` (data-model.md) — `from(Booking)` factory reading `slot.session.doctorProfile.account.name`, `slot.startTime`, `appointmentType.name`, `status`, `slot.status`.
- [x] T005 [US1] Create `backend/src/main/java/com/cms/patient/record/api/PatientBookingHistoryController.java`: `GET /api/v1/clinics/{clinicId}/patients/{patientId}/bookings`, reusing `PatientDetailController`'s exact clinic-ownership check pattern, calling `bookingRepository.findByPatient_IdOrderBySlot_Session_SessionDateDesc(patientId, pageable)` (contracts/patient-booking-history.md). **Real bug found and fixed** (caught by T002's own test): added `.requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/patients/*")` and `.../patients/*/bookings` to `SecurityConfig.java`'s `/api/v1/clinics/**` chain — the first line fixes the pre-existing gap on `PatientDetailController` (which had silently relied on its own internal check throwing an exception for unauthenticated callers, a 500 not real protection), the second covers this feature's own new endpoint.
- [x] T006 [US1] Edit `backend/src/main/java/com/cms/patient/record/dto/PatientSearchResultResponse.java`: add `anonymizedAt` field and update `.from(Patient)` (data-model.md Decision 3) — used by both existing call sites (`ClinicPatientSearchController`, `PatientDetailController`) automatically, no controller change needed for either.
- [x] T007 [US1] Edit `frontend/src/features/patient-search/api.ts`: add `anonymizedAt` to the `PatientSearchResult` type, add `listPatientBookings(clinicId, patientId, token, params)` returning the new `PatientBookingHistoryResult` type (data-model.md).
- [x] T008 [US1] Create `frontend/src/routes/staff/PatientHubPage.tsx`: fetches `getPatient` (existing) + `listPatientBookings` (new, paginated via the existing `PaginationControls` component already used in `PatientSearch.tsx` — not new pagination UI) on mount; renders Overview (name/phone/anonymization state), Bookings (paginated list, all statuses, newest first), and Consultations/Prescriptions/External Records (each: the same page of bookings, filtered to bookings whose session has actually started — see T011's note — as rows linking to `/staff/clinics/${clinicId}/bookings/${bookingId}/consultation-note` / `.../prescription` / `.../external-record`) as client-side tabs (research.md Decision 4, no nested routes). Empty states for zero bookings.
- [x] T009 [US1] Edit `frontend/src/App.tsx`: add `<Route path="patients/:patientId" element={<PatientHubPage />} />` under `ClinicShell` (alongside the existing `patients/:patientId/anonymize` route). Confirmed no route-matching ambiguity with the existing static `patients/search` route — React Router v6 ranks static segments above `:patientId` regardless of declaration order.
- [x] T010 [US1] Edit `frontend/src/features/patient-search/PatientSearch.tsx`: wrap each result's name/phone block in a `<Link to={`/staff/clinics/${clinicId}/patients/${patient.patientId}`}>` into the hub — keeping the existing, separately-styled "Anonymize" link exactly as the 2026-09-10 audit fixed it (a single combined link back to the destructive path must not be reintroduced).
- [x] T011 [US1] Add `frontend/tests/patient-search/PatientHubPage.test.tsx`: renders Overview identity, renders the real booking list, Consultations/Prescriptions/External Records rows link to the correct existing per-booking URLs, empty states at zero bookings. 7 tests, all passing. **This test caught a real bug in T008's own first draft**: filtering the clinical-record tabs on `slotStatus !== 'OPEN'` alone let a genuinely upcoming, not-yet-started `BOOKED` appointment appear in the Consultations tab — `BOOKED` doesn't distinguish "upcoming" from "happened, not yet marked complete." Fixed by also requiring `sessionDate <= today`.
- [x] T012 [US1] Update `frontend/tests/patient-search/PatientSearch.test.tsx`: add a test confirming each result now links into the hub, alongside the existing (unchanged) Anonymize link assertion. Also updated one existing test's own assertion, which explicitly checked the patient name was *not* a link (the 2026-09-10 audit's fix) — now updated to confirm it links into the hub specifically, not the anonymize path, preserving that audit's real safety property while reflecting the new, intentional behavior.

**Checkpoint**: Staff can reach a patient's hub from search and see their real history at this clinic.

---

## Phase 4: User Story 2 - Anonymized patients show their real, current state (Priority: P2)

**Goal**: The hub's Overview always reflects current anonymization state, never a stale value.

**Independent Test**: Anonymize a patient via the existing flow, then open their hub and confirm it shows the current scrubbed state.

### Implementation for User Story 2

- [x] T013 [US2] Confirm (and adjust if needed) `PatientHubPage.tsx`'s Overview rendering: an anonymized patient (`anonymizedAt !== null`) shows a clear, explicit "Anonymized" indicator alongside the already-scrubbed name/phone values — not just the scrubbed text with no explanation of why. Built directly into T008 (a red `Badge` next to the name).
- [x] T014 [US2] Add a test case to `PatientHubPage.test.tsx`: given a patient with `anonymizedAt` set, the hub shows the anonymized indicator and the scrubbed name/phone exactly as returned by the API (proving the hub reads live data, not a cached pre-anonymization value it fetched from elsewhere). Built directly into T011's test suite.

**Checkpoint**: Anonymization state is accurate and explicit wherever the hub shows patient identity.

---

## Phase 5: User Story 3 - No new edit capability, no authorization bypass (Priority: P2)

**Goal**: Confirm by construction and by test that this feature adds zero edit/delete UI and zero authorization change.

**Independent Test**: Confirm no edit/delete control exists anywhere for an existing consultation note or prescription; confirm a non-treating doctor following a hub link still gets the existing, unchanged rejection.

### Implementation for User Story 3

- [x] T015 [P] [US3] Run `backend/src/test/java/com/cms/clinical`'s existing test classes (`ConsultationNoteService`/`PrescriptionService`/`ExternalRecordReferenceService` and their controllers) unmodified — confirm all pass, proving this feature added no regression to 030/031/032's authorization or immutability guarantees (SC-005). Confirmed: all fail only with the established Testcontainers/Docker signature (grep-verified against every result XML), zero non-Docker failures — including `PatientDetailControllerTest`, itself blocked the same way, whose own `SecurityConfig` gap this feature happened to fix.
- [x] T016 [P] [US3] Run `backend/src/test/java/com/cms/patient/record`'s existing anonymization test classes (033) unmodified — confirm all pass (SC-005). Same result: Docker-only, zero non-Docker failures.
- [x] T017 [US3] Add a frontend test (in `PatientHubPage.test.tsx` or a dedicated file) confirming the hub's Consultations/Prescriptions/External Records rows render as plain navigation `Link`s with no accompanying edit/delete button, select, or form control of any kind — a direct, automated check for SC-004, not just a manual read-through. Built directly into T011's test suite (the "renders no edit or delete control" test).

**Checkpoint**: All 3 user stories independently functional; zero new capability beyond navigation exists.

---

## Phase 6: Polish

- [x] T018 `cd backend && gradle compileJava compileTestJava spotlessCheck` — clean compile, zero new formatting violations. Confirmed.
- [x] T019 `cd backend && gradle test --tests "*PatientBookingHistory*"` — new tests pass; then the full backend suite, grep-verified zero non-Docker regressions (same method as 048/051). Confirmed: 8/8 new tests pass; full suite 328 tests, 226 failing, all grep-verified Docker-signature-only (same count as 051's own baseline — zero new failures).
- [x] T020 `cd frontend && npx tsc -b && npm run lint` — zero type errors, zero new lint errors. Confirmed: 0 errors; only pre-existing warnings elsewhere in the codebase (including the same `set-state-in-effect` style warning already accepted on several other existing files, now also on the new `PatientHubPage.tsx`).
- [x] T021 `cd frontend && npm run test -- --run` — full suite, zero regression, count increases by the new tests. Confirmed: 275/275 passing (268 baseline + 7 new).
- [x] T022 Manual live verification per `quickstart.md` steps 7-13. **Verified live against the real backend + real Postgres** (reusing the clinic/session registered during 051's own live verification): signed in as ClinicAdmin, confirmed 047/048's sidebar and dashboard still work correctly, opened Patient Search and ran a real search (200 OK, correctly rendering "No matching patients." for this clinic's currently-empty patient list — the extended `PatientSearchResultResponse` with `anonymizedAt` round-trips cleanly). Directly verified the new booking-history endpoint against the live backend: a nonexistent patient id returns the exact contract-specified `404 PATIENT_NOT_FOUND`; both the new endpoint and the now-fixed pre-existing `PatientDetailController` correctly return `401` for an unauthenticated request, proving the `SecurityConfig` fix works in the real running app, not only in mocked contract tests. Steps involving real booking/note/prescription data (9-10) were not additionally exercised live — no patient with real bookings existed in this clinic and creating one would require a full schedule/session/booking setup — relying instead on T001/T002/T011's passing tests, which already exercise that exact logic (including, for T011, the real bug caught in the started-vs-upcoming filter).
- [x] T023 Update `backlog/progress.md`'s row for `049-patient-context-clinical-history-hub`.

---

## Dependencies & Execution Order

- US1 (T001-T012) is the MVP — Overview/Bookings/Consultations/Prescriptions/External Records all ship together since a hub with only some sections isn't independently useful (FR-007 requires all 5).
- US2 (T013-T014) depends on US1's Overview existing (T008) — it's a correctness refinement + dedicated test on top of it.
- US3 (T015-T017) depends on US1's hub existing (its rows are what T017 inspects) but its regression tests (T015/T016) can run any time.
- Polish depends on all 3 stories complete.

## Notes

- Total: 23 tasks.
- Second full-stack feature in this design-system wave (after 048) — backend scope deliberately minimal: 1 repository method, 2 new DTOs, 1 new controller, 1 existing-DTO field addition, reusing 2 existing authorization gates (clinic-staffed check, treating-doctor check) verbatim.
- No task edits any file under `com.cms.clinical` (consultation notes/prescriptions/external records) or their existing frontend pages — the hub only ever links to them (FR-004/FR-006/FR-008).
