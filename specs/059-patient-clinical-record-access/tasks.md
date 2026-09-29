---

description: "Task list for Patient Clinical Record Access"
---

# Tasks: Patient Clinical Record Access

**Input**: Design documents from `/specs/059-patient-clinical-record-access/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/patient-clinical-record-access.md, quickstart.md

**Tests**: Included per this project's constitution (Principle I, Test-First Development, NON-NEGOTIABLE). The `clinical` module currently has only integration-tier tests (Testcontainers-gated, unexecuted in this sandbox); this feature adds the module's first `unit/` and `contract/` tiers so its new logic has real, executable coverage in this sandbox, matching the pattern already established in `scheduling`/`booking`.

**Organization**: Tasks are grouped by user story (US1/US2/US3, matching spec.md's priorities) so each is independently implementable and testable. The bulk availability check (FR-004) spans all three record types by nature (one query per type, unioned) and cannot be meaningfully split into thirds without rebuilding the same endpoint three times, so it is built complete in Foundational; each story still adds its own contribution's worth of coverage for it.

## Phase 1: Setup

No setup tasks. This feature adds zero new dependencies (plan.md Technical Context) and extends one existing module (`clinical`) plus one existing repository (`booking`) on their existing stack.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared ownership-check plumbing and the availability endpoint every story's "can I tell what's worth opening" flow depends on. Must complete before any user story phase.

- [x] T001 [P] Add `findByIdAndPatient_PatientAccount_Id(UUID id, UUID patientAccountId)` to `backend/src/main/java/com/cms/booking/repository/BookingRepository.java` (data-model.md — a derived query, same style as the existing `findByPatient_PatientAccount_IdOrderByCreatedAtDesc` on the same entity path).
- [x] T002 [P] Add `backend/src/test/java/com/cms/booking/unit/BookingRepositoryPatientOwnershipTest.java` if a pure-unit shape is feasible for a Spring Data derived query (likely not — Spring Data derived queries have no logic to unit-test); otherwise fold verification into T001's own integration coverage in each story below (`@DataJpaTest` is Spring-context-gated, so this may need to be integration-tier — assess and note the decision when implementing).
- [x] T003 Create `backend/src/main/java/com/cms/clinical/service/ClinicalRecordAvailabilityService.java` with `findBookingIdsWithAnyRecord(Collection<UUID> bookingIds, UUID patientAccountId) -> Set<UUID>` (data-model.md), backed by three new bulk-exists queries — one added to each of `ConsultationNoteRepository`, `PrescriptionRepository`, `ExternalRecordReferenceRepository` (each additionally scoped through `booking.patient.patientAccount.id`, per data-model.md's exact query shape). Depends on T001.
- [x] T004 Create `backend/src/main/java/com/cms/clinical/api/PatientClinicalRecordController.java` with `GET /api/v1/patients/bookings/clinical-record-availability?bookingIds=...` (contracts/patient-clinical-record-access.md), returning `{ "bookingIdsWithRecords": [...] }`; `400` for a missing/empty `bookingIds` param. This is the same controller US1-US3 will each add their own endpoint method to. Depends on T003.
- [x] T005 Add the `.requestMatchers(HttpMethod.GET, "/api/v1/patients/bookings/clinical-record-availability")...authenticated()` entry to `backend/src/main/java/com/cms/patient/account/config/SecurityConfig.java` — this codebase has a documented, previously-real recurring bug where a new authenticated path silently falls through to `anyRequest().permitAll()` when this step is skipped; do not defer it. Depends on T004.
- [x] T006 [P] Add unit test `backend/src/test/java/com/cms/clinical/unit/ClinicalRecordAvailabilityServiceTest.java` (pure Mockito, no Spring context — this module's first unit tier): confirms the union logic across all three repositories, and that a booking id not belonging to the caller's patient account is never included regardless of whether it has records.
- [x] T007 [P] Add contract test `backend/src/test/java/com/cms/clinical/contract/PatientClinicalRecordControllerContractTest.java` (`@WebMvcTest`, this module's first contract tier): `200` with the expected shape for a valid request, `400` for a missing `bookingIds` param, `401` for a missing bearer token.
- [x] T008 [P] Create `frontend/src/features/patient-clinical-records/api.ts` with the availability client call (`getClinicalRecordAvailability(bookingIds, token) -> Set<string>`) and typed error handling, mirroring this codebase's existing feature `api.ts` conventions (contracts/patient-clinical-record-access.md).
- [x] T009 [P] Create an empty `frontend/src/features/patient-clinical-records/VisitRecordSection.tsx` shell (props: `bookingId`) and render it from `PatientBookingDetailPage` in `frontend/src/routes/patient/PatientPages.tsx`, alongside the existing `QueuePositionIndicator`/`CancelBookingButton` (plan.md Project Structure — extends the existing route, no new route). Each user story below fills in one section of this component; it renders nothing yet.

**Checkpoint**: the availability endpoint works end-to-end and is secured; the patient-facing visit page has a place for record content to appear. User story implementation can begin.

---

## Phase 3: User Story 1 - Reading the consultation note from a past visit (Priority: P1) 🎯 MVP

**Goal**: A patient can find, from their own booking history, which past visits have a consultation note, open one, and read it — strictly read-only, refused for any booking that isn't theirs.

**Independent Test**: Sign in as a patient with a past booking that has a consultation note, find that visit from "My Bookings," open it, and confirm the note's content is shown exactly as written, with no edit control anywhere (quickstart.md Scenario 1, consultation-note portion).

### Tests for User Story 1

- [x] T010 [P] [US1] Unit test `backend/src/test/java/com/cms/clinical/unit/ConsultationNoteServiceTest.java` (this service's first unit tier — existing coverage is integration-only): `getForPatient` returns the note when the booking belongs to the caller's patient account and a note exists; returns `null`/empty when the booking is theirs but no note exists; throws `BookingNotFoundException` when the booking isn't theirs (mocked `BookingRepository`, no real treating-doctor logic involved).
- [x] T011 [P] [US1] Contract test `backend/src/test/java/com/cms/clinical/contract/PatientConsultationNoteControllerContractTest.java`: `200` with the note body when it exists, `200` with a `null` body when it doesn't, `404` for a booking that isn't the caller's, `401` for a missing bearer token.
- [x] T012 [P] [US1] Integration test `backend/src/test/java/com/cms/clinical/integration/PatientConsultationNoteAccessTest.java`: end-to-end against a real booking/note/patient-account, including the refusal case for a different patient's booking (integration tier, written/compiled but unexecuted per this sandbox's standing Docker limitation).
- [x] T013 [P] [US1] Add `frontend/tests/patient-clinical-records/VisitRecordSection.test.tsx` (new file): renders the consultation note section when present, renders a clear "nothing written yet" state when absent, renders no edit control.
- [x] T014 [P] [US1] Add `frontend/tests/patient-bookings/MyBookings.test.tsx` (new file, or extend if one exists by the time this runs): the availability indicator appears for a booking the availability endpoint reports as having a record, and does not for one it doesn't.

### Implementation for User Story 1

- [x] T015 [US1] Add `getForPatient(UUID bookingId, UUID patientAccountId)` to `backend/src/main/java/com/cms/clinical/service/ConsultationNoteService.java` (data-model.md): loads the booking via `BookingRepository.findByIdAndPatient_PatientAccount_Id` (404 if absent), then `consultationNoteRepository.findByBooking_Id` (research.md Decision 2 — no new "is this purged" check, the same lookup the treating-doctor path already uses). Depends on T001.
- [x] T016 [US1] Add `GET /api/v1/patients/bookings/{bookingId}/consultation-note` to `PatientClinicalRecordController.java` (contracts/patient-clinical-record-access.md), reusing `ConsultationNoteResponse` unchanged (research.md Decision 4). Depends on T015.
- [x] T017 [US1] Add the corresponding `.requestMatchers(HttpMethod.GET, "/api/v1/patients/bookings/*/consultation-note")...authenticated()` entry to `SecurityConfig.java`. Depends on T016.
- [x] T018 [P] [US1] Add `getConsultationNote` to `frontend/src/features/patient-clinical-records/api.ts`.
- [x] T019 [US1] Fill in the consultation-note section of `VisitRecordSection.tsx`: fetches and renders the note, or a clear empty state. Depends on T009, T018.
- [x] T020 [US1] Wire the availability indicator into `frontend/src/features/patient-bookings/MyBookings.tsx`: calls the availability endpoint (T008) for the currently-visible page of bookings once loaded, shows an indicator per row. Depends on T008.
- [x] T021 [US1] Live-verify via `quickstart.md` Scenarios 1 (consultation-note portion), 2, and 3 against the running dev servers. **Done 2026-09-23**: signed up two fresh patient accounts, booked 3 real slots with Gauresh Kumar (Star Clinic) as one of them, wrote a real consultation note as the treating doctor. Scenario 1: "Record available" indicator shown correctly in My Bookings for the documented visit only; opening it displayed the note's exact content, no edit/delete control anywhere. Scenario 2: the undocumented visit showed a clean "No consultation note for this visit" state, not an error. Scenario 3: navigating directly to the documented visit's URL while signed in as the *other* patient account returned `404` on all four endpoints (confirmed via network log) — refused exactly like an invalid booking id, no cross-patient data leak.

**Checkpoint**: User Story 1 fully functional and independently testable — a patient can find and read their own consultation notes, refused for anyone else's, with automated coverage and a live pass.

---

## Phase 4: User Story 2 - Reading prescriptions from a past visit (Priority: P2)

**Goal**: A patient can read every prescription (and its items) tied to one of their own past visits, on the same visit page US1 established.

**Independent Test**: Sign in as a patient with a past booking that has one or more prescriptions, open that visit, and confirm every prescription and its items are shown, none belonging to another patient (quickstart.md Scenario 1, prescriptions portion).

### Tests for User Story 2

- [x] T022 [P] [US2] Unit test `backend/src/test/java/com/cms/clinical/unit/PrescriptionServiceTest.java`: `listForPatient` returns every prescription (with items) for the caller's own booking; returns an empty list when the booking is theirs but has none; throws `BookingNotFoundException` when the booking isn't theirs.
- [x] T023 [P] [US2] Contract test `backend/src/test/java/com/cms/clinical/contract/PatientPrescriptionControllerContractTest.java`: `200` with a populated array, `200` with an empty array, `404` for a booking that isn't the caller's, `401` for a missing bearer token.
- [x] T024 [P] [US2] Integration test `backend/src/test/java/com/cms/clinical/integration/PatientPrescriptionAccessTest.java`: end-to-end including the refusal case.
- [x] T025 [P] [US2] Extend `frontend/tests/patient-clinical-records/VisitRecordSection.test.tsx`: renders every prescription and its items when present, renders a clear empty state when absent, renders no edit control.

### Implementation for User Story 2

- [x] T026 [US2] Add `listForPatient(UUID bookingId, UUID patientAccountId)` to `backend/src/main/java/com/cms/clinical/service/PrescriptionService.java`, mirroring T015's shape exactly. Depends on T001.
- [x] T027 [US2] Add `GET /api/v1/patients/bookings/{bookingId}/prescriptions` to `PatientClinicalRecordController.java`, reusing `PrescriptionResponse`/`PrescriptionItemResponse` unchanged. Depends on T026.
- [x] T028 [US2] Add the corresponding `.requestMatchers(HttpMethod.GET, "/api/v1/patients/bookings/*/prescriptions")...authenticated()` entry to `SecurityConfig.java`. Depends on T027.
- [x] T029 [P] [US2] Add `getPrescriptions` to `frontend/src/features/patient-clinical-records/api.ts`.
- [x] T030 [US2] Fill in the prescriptions section of `VisitRecordSection.tsx`. Depends on T019, T029.
- [x] T031 [US2] Live-verify via `quickstart.md` Scenario 1 (prescriptions portion) against the running dev servers. **Done 2026-09-23**: wrote a real prescription (Cetirizine, 1 item) as the treating doctor for the same documented visit; the patient's visit page displayed it under "Prescriptions" with the exact content the doctor entered, no edit control.

**Checkpoint**: User Stories 1 and 2 both independently functional — nothing from US1 was removed or changed.

---

## Phase 5: User Story 3 - Reading external record references from a past visit (Priority: P3)

**Goal**: A patient can read every external record reference tied to one of their own past visits, completing the same visit page.

**Independent Test**: Sign in as a patient with a past booking that has one or more external record references, open that visit, and confirm each reference is shown, none belonging to another patient (quickstart.md Scenario 1, external-records portion).

### Tests for User Story 3

- [x] T032 [P] [US3] Unit test `backend/src/test/java/com/cms/clinical/unit/ExternalRecordReferenceServiceTest.java`: `listForPatient` returns every reference for the caller's own booking; returns an empty list when the booking is theirs but has none; throws `BookingNotFoundException` when the booking isn't theirs.
- [x] T033 [P] [US3] Contract test `backend/src/test/java/com/cms/clinical/contract/PatientExternalRecordReferenceControllerContractTest.java`: `200` with a populated array, `200` with an empty array, `404` for a booking that isn't the caller's, `401` for a missing bearer token.
- [x] T034 [P] [US3] Integration test `backend/src/test/java/com/cms/clinical/integration/PatientExternalRecordReferenceAccessTest.java`: end-to-end including the refusal case.
- [x] T035 [P] [US3] Extend `frontend/tests/patient-clinical-records/VisitRecordSection.test.tsx`: renders every external record reference when present, renders a clear empty state when absent.

### Implementation for User Story 3

- [x] T036 [US3] Add `listForPatient(UUID bookingId, UUID patientAccountId)` to `backend/src/main/java/com/cms/clinical/service/ExternalRecordReferenceService.java`, mirroring T015/T026's shape exactly. Depends on T001.
- [x] T037 [US3] Add `GET /api/v1/patients/bookings/{bookingId}/external-record-references` to `PatientClinicalRecordController.java`, reusing `ExternalRecordReferenceResponse` unchanged. Depends on T036.
- [x] T038 [US3] Add the corresponding `.requestMatchers(HttpMethod.GET, "/api/v1/patients/bookings/*/external-record-references")...authenticated()` entry to `SecurityConfig.java`. Depends on T037.
- [x] T039 [P] [US3] Add `getExternalRecordReferences` to `frontend/src/features/patient-clinical-records/api.ts`.
- [x] T040 [US3] Fill in the external-record-references section of `VisitRecordSection.tsx`, completing the full visit view. Depends on T030, T039.
- [x] T041 [US3] Live-verify via `quickstart.md` Scenario 1 (external-records portion) and Scenario 4 (purge parity) against the running dev servers. **Done 2026-09-23** (Scenario 1 portion): wrote a real external record reference (Lab Report, City Diagnostics Lab, dated 2026-09-20) as the treating doctor; the patient's visit page displayed it correctly under "External records." **Scenario 4 (purge parity) not live-exercised**: it requires either a real 3-year-aged record or directly invoking/fast-forwarding `RetentionPurgeService.purge()` against a specific row, and this sandbox's safety guardrails correctly block direct DB/backend-internal manipulation outside the app's own UI (same class of restriction noted in HANDOFF.md Part 5 §3). Relying instead on the code-level guarantee already confirmed during `/speckit-analyze` and in research.md Decision 2: the new patient-facing lookups (`ConsultationNoteRepository.findByBooking_Id` etc.) are the *exact same* queries the existing treating-doctor path already uses, so a purged/deleted row is structurally invisible to both surfaces with no separate purge-aware filter to drift out of sync — there is no code path by which this could differ from staff-side behavior. Worth a real live pass in an environment where a 3-year-old record naturally exists.

**Checkpoint**: All three user stories independently functional — the full feature as specified.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T042 [P] `cd backend && ./gradlew spotlessApply test --tests "com.cms.clinical.*" --tests "com.cms.booking.*"` — full unit + contract suite green (integration tests compiled, unexecuted per this sandbox's standing Docker limitation).
- [x] T043 [P] `cd frontend && npx tsc -b` — zero type errors.
- [x] T044 [P] `cd frontend && npm run lint` — zero new warnings in any file this feature touches.
- [x] T045 `cd frontend && npm run test -- --run` — full suite green, zero regressions.
- [x] T046 Grep `backend/src/main/java/com/cms/patient/account/config/SecurityConfig.java` for all four new paths (`clinical-record-availability`, `consultation-note`, `prescriptions`, `external-record-references`) and confirm each has an explicit `.authenticated()` matcher — this codebase's own recurring bug class (a new path silently falling through to `anyRequest().permitAll()`) has been real twice already this session; do not skip this check.
- [x] T047 Confirm no existing staff-side endpoint, test, or authorization path (`TreatingDoctorAuthorizationService`, the `/api/v1/clinics/{clinicId}/bookings/{bookingId}/...` routes) was touched by this feature — grep for any accidental edit outside `com.cms.clinical`'s new patient-facing surface and the one new `BookingRepository` method (spec.md FR-007/FR-008).

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: T001-T009. Blocks every user story — the ownership-check repository method (T001) is used by every story's service method; the availability endpoint (T003-T007) and the `VisitRecordSection` shell (T009) are what each story's frontend work fills in.
- **US1 (T010-T021)**: depends only on Foundational. Fully independent of US2/US3.
- **US2 (T022-T031)**: depends on Foundational (T001) and, for its frontend task, on US1's `VisitRecordSection.tsx` edit (T019) — the one place a later story's UI work builds directly on an earlier story's same-file edit, not a hidden coupling of business logic (mirrors 057's own T025-on-T018 precedent).
- **US3 (T032-T041)**: depends on Foundational (T001) and, for its frontend task, on US1 (T019) and US2 (T030)'s `VisitRecordSection.tsx` edits for the same reason.
- **Polish (Phase 6)**: depends on all three user stories being complete.

## Parallel Example: Foundational

```bash
# T001, T002 touch different concerns (repository method vs. its own test) and can start together;
# T006-T009 all touch different files once T003-T005 land:
Task: "Add findByIdAndPatient_PatientAccount_Id to BookingRepository.java"
Task: "Unit test ClinicalRecordAvailabilityService"
Task: "Contract test PatientClinicalRecordController"
Task: "Create frontend patient-clinical-records/api.ts"
Task: "Create the VisitRecordSection.tsx shell"
```

## Parallel Example: User Story 1 Tests

```bash
# T010-T014 touch different files - parallelizable:
Task: "Unit test ConsultationNoteService.getForPatient"
Task: "Contract test PatientConsultationNoteController"
Task: "Integration test: patient consultation-note access + refusal"
Task: "Frontend test: VisitRecordSection consultation-note section"
Task: "Frontend test: MyBookings availability indicator"
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 2: Foundational.
2. Complete Phase 3: User Story 1 — a patient can find and read their own consultation notes, refused for anyone else's. Deployable and demoable on its own.
3. **STOP and VALIDATE**: run `quickstart.md` Scenarios 1 (consultation-note portion), 2, and 3.

### Incremental Delivery

1. Foundational → US1 (MVP) → validate → demo.
2. US2 (prescriptions) → validate → demo.
3. US3 (external record references) → validate → demo.
4. Polish once all three are in.

## Notes

- Total: 47 tasks (9 Foundational + 12 US1 + 10 US2 + 10 US3 + 6 Polish).
- No new migration anywhere in this feature (plan.md Technical Context) — no schema change of any
  kind; every new query reads existing tables.
- This feature is the `clinical` module's first use of unit and contract test tiers — previously
  it only had integration (Testcontainers, Docker-gated) tests. The new unit/contract tests give
  this feature real, executable coverage in this sandbox for the first time in this module.
- `ConsultationNoteResponse`/`PrescriptionResponse`/`PrescriptionItemResponse`/
  `ExternalRecordReferenceResponse` need zero code changes anywhere in this feature — they are
  reused exactly as they already exist for the staff-side endpoints (research.md Decision 4).
- The existing staff-side treating-doctor-only endpoints, `TreatingDoctorAuthorizationService`,
  and every other role's access are untouched by every task above — this is purely additive, and
  T047 exists specifically to confirm that stayed true.
