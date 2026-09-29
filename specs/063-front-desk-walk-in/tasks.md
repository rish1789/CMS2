---
description: "Task list for 063-front-desk-walk-in"
---

# Tasks: Front-Desk Walk-In Registration with a Walk-In Line

**Input**: `specs/063-front-desk-walk-in/` (spec, plan, research, data-model, contracts, quickstart)

**Tests**: REQUIRED (Constitution I). Each implementation task follows its failing test.

**Environment**:
- Gradle: `"/c/Users/risha/AppData/Local/Temp/gradle-8.10/bin/gradle.bat" -p backend …`. Stop the running backend and `rm -rf backend/build` on "not a regular file".
- Integration tests are Docker-gated here (compile them; verify in a real dev/CI environment).

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

- [x] T001 Create `backend/src/main/resources/db/migration/V39__front_desk_walk_in.sql` per data-model.md:
  - `booking.visit_reason VARCHAR(40)`, `booking.visit_reason_detail VARCHAR(200)`;
  - check constraint `ck_booking_visit_reason_other_detail`;
  - `patient.email VARCHAR(254)`;
  - `slot.appeared_at TIMESTAMPTZ`, `slot.completed_at TIMESTAMPTZ`.
- [x] T002 [P] Integration test `backend/src/test/java/com/cms/booking/integration/VisitReasonConstraintTest.java` (write before T001 is relied on): inserting a booking with `visit_reason='OTHER'` and null or blank detail is rejected by the database; `OTHER` + detail and any non-OTHER reason with null detail are accepted (Constitution I: migration invariant).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: domain fields, the untimed-slot concept, and every guard that keeps untimed walk-in slots safe inside Fixed-Time sessions (research.md Decision 3). All stories depend on this.

### Tests first

- [x] T003 [P] Unit test `backend/src/test/java/com/cms/scheduling/unit/SlotTimestampTest.java`:
  - `SlotAppearedService.markAppeared` stamps `appearedAt` (BOOKED→APPEARED and NO_SHOW→APPEARED);
  - `SlotCompletionService` manual completion stamps `completedAt`;
  - automatic completion stamps `completedAt`;
  - completing an **untimed** slot skips the not-yet-started check (no NPE, completes).
- [x] T004 [P] Unit test `backend/src/test/java/com/cms/scheduling/unit/QueueSlotServiceWalkInTest.java`:
  - `issueNextWalkInSlot` on a FIXED_TIME session returns an untimed slot with token max+1 (1 when none);
  - on a QUEUE session it throws `NotAFixedTimeSessionException`;
  - the existing `issueNextSlot` on a FIXED_TIME session still throws `NotAQueueSessionException`.
- [x] T005 [P] Unit test `backend/src/test/java/com/cms/booking/unit/UntimedSlotGuardsTest.java`:
  - `WaitlistBumpListener` ignores a `BookingCancelledEvent` for an untimed slot (no `matchAndOffer`) and still bumps for a timed Fixed-Time slot. This is the single guard for every event publisher (analyze U1);
  - `StaffBookingService.bookSlot` and `PatientBookingService.bookSlot` on an untimed slot throw `SlotNotFoundException`;
  - `SessionPartialCancellationService` skips untimed slots without NPE;
  - `QueuePositionService.positionOf` for a Fixed-Time walk-in returns applicable with position = untimed BOOKED walk-ins with a lower token + 1, and not-applicable for a timed Fixed-Time booking.
- [x] T006 [P] Contract test `backend/src/test/java/com/cms/booking/contract/WalkInSelfCancelContractTest.java`: the patient cancel endpoint on a walk-in (untimed-slot) booking → `409 WALK_IN_NOT_SELF_CANCELLABLE`.
- [x] T007 [P] Integration test `backend/src/test/java/com/cms/scheduling/integration/UntimedSlotSweepsTest.java`:
  - an untimed BOOKED walk-in slot past the no-show grace period is not marked NO_SHOW;
  - an untimed APPEARED slot is not auto-completed;
  - an untimed OPEN slot never appears in either patient open-slot listing query;
  - the session list's `totalSlotCount`/`bookedSlotCount` for a Fixed-Time session exclude untimed slots.

### Implementation

- [x] T008 Add `VisitReason` enum `backend/src/main/java/com/cms/booking/domain/VisitReason.java`. Add `visitReason`/`visitReasonDetail` fields + getters to `Booking.java`, plus a walk-in constructor carrying them with `source = WALK_IN`. Add `email` + getter to `patient/record/domain/Patient.java` (constructor overload with email). Add `appearedAt`/`completedAt` + `markAppeared(Instant)`/`markCompleted(Instant)` helpers and `isUntimed()` (`startTime == null`) to `scheduling/domain/Slot.java`.
- [x] T009 `SlotAppearedService` sets `appearedAt`; `SlotCompletionService` sets `completedAt` in both paths and skips the not-yet-started check when `slot.isUntimed()`. T003 goes green.
- [x] T010 `QueueSlotService.issueNextWalkInSlot(UUID)`: FIXED_TIME only, sharing the existing retry/max-token core with `issueNextSlot`. T004 goes green.
- [x] T011 Guards:
  - `waitlist/service/WaitlistBumpListener`: return early for `slot.isUntimed()`;
  - `StaffBookingService`/`PatientBookingService.bookSlot`: refuse untimed;
  - `SessionPartialCancellationService`: skip untimed;
  - `QueuePositionService`: Fixed-Time walk-in position;
  - `PatientBookingCancellationController`: untimed → new `WalkInNotSelfCancellableException`, mapped to 409 in `BookingExceptionHandler`.

  T005 and T006 go green.
- [x] T012 `SlotRepository`:
  - add `AND s.startTime IS NOT NULL` to `findBookedFixedTimeCandidatesForNoShow`, `findAppearedFixedTimeCandidatesForAutoCompletion`, and both `findOpenFixedTimeSlots*` queries (+ their count queries);
  - `countBySessionIdIn` counts timed slots only for FIXED_TIME sessions.

  T007's assertions are covered.
- [x] T013 `patient/record/service/PatientAnonymizationService`: also clear `email` on anonymization (Constitution IV). Extend `PatientAnonymizationTest` with an email assertion (test first).

**Checkpoint**: untimed walk-in slots are safe everywhere; the timestamps work.

---

## Phase 3: User Story 1 - Register a walk-in from one front-desk screen (Priority: P1) 🎯 MVP

**Goal**: FR-001–FR-011, FR-017, FR-018, FR-021. **Independent Test**: quickstart.md Scenarios 1–4, 7.

### Tests first

- [x] T014 [P] [US1] Unit test `backend/src/test/java/com/cms/booking/unit/FrontDeskWalkInServiceTest.java`:
  - FIXED_TIME registration issues a walk-in slot, sets it BOOKED, saves a WALK_IN booking with the visit reason and the locked fee, creates the inbox walk-in item, and touches no timed slot;
  - QUEUE registration uses `issueNextSlot`, the booking is WALK_IN, inbox item created;
  - missing visit reason → `VISIT_REASON_REQUIRED`;
  - OTHER without detail, or detail over 200 characters → `VISIT_REASON_DETAIL_REQUIRED`;
  - neither patientId nor name → `PATIENT_REQUIRED`;
  - bad email → `INVALID_EMAIL`; bad phone → existing `INVALID_MOBILE_NUMBER`;
  - duplicate (patient already ACTIVE in this session) without confirm → `DUPLICATE_WALK_IN`; with confirm → proceeds;
  - rejected clinic → `CLINIC_NOT_ACCEPTING_APPOINTMENTS` before any write;
  - unauthorized role → `FORBIDDEN`;
  - a patientId from another clinic → `PATIENT_NOT_FOUND`;
  - the response carries tokenNumber and walkInPosition (FIXED_TIME) / null position (QUEUE).
- [x] T015 [P] [US1] Contract test `backend/src/test/java/com/cms/booking/contract/FrontDeskWalkInControllerContractTest.java`: `POST /api/v1/clinics/{clinicId}/walk-ins` → 201 with the contract §1 body; each error code maps to its documented status; 401 without a token.
- [x] T016 [P] [US1] Integration test `backend/src/test/java/com/cms/booking/integration/FrontDeskWalkInRegistrationTest.java`:
  - a real FIXED_TIME session with a booked timed appointment: register a new patient (with email) → W1, then another → W2, positions 1 and 2;
  - the booked appointment and all timed slots unchanged (SC-003);
  - the inbox WALK_IN item exists;
  - a QUEUE session registration gets the next token after an existing booked token and is WALK_IN;
  - duplicate → 409, then confirm → 201.
- [x] T017 [P] [US1] Integration test `backend/src/test/java/com/cms/scheduling/integration/SessionListWalkInCountsTest.java`: the session list reports `walkInsWaiting` and `inWithDoctor` correctly (0/false → after registration 2/false → after Appeared 1/true); QUEUE sessions always `inWithDoctor=false`.
- [x] T018 [P] [US1] Frontend tests in `frontend/tests/front-desk-walk-in/FrontDeskWalkInPage.test.tsx`:
  - the steps render in order;
  - "Other" requires text;
  - a new patient's phone match offers the existing patient;
  - the session list shows status, booked, waiting and the free/busy hint; a session whose end has passed with nobody waiting or in with the doctor is hidden;
  - a not-ready doctor's session is disabled with the reason;
  - a submit with 409 DUPLICATE_WALK_IN shows a confirm prompt that resubmits with `confirmDuplicate`;
  - the result shows W-number + position (Fixed-Time) or token only (Queue) and the fee.

### Implementation

- [x] T019 [US1] DTOs `FrontDeskWalkInRequest`/`FrontDeskWalkInResponse` (contract §1) in `backend/src/main/java/com/cms/booking/dto/`. Exceptions `VisitReasonRequiredException`, `VisitReasonDetailRequiredException`, `PatientRequiredException`, `InvalidEmailException`, `DuplicateWalkInException` in `booking/exception/`, mapped in `BookingExceptionHandler`.
- [x] T020 [US1] `backend/src/main/java/com/cms/booking/service/FrontDeskWalkInService.java` per research.md Decision 4, including the duplicate query `BookingRepository.existsBySlot_Session_IdAndPatient_IdAndStatus`. T014 goes green.
- [x] T021 [US1] `backend/src/main/java/com/cms/booking/api/FrontDeskWalkInController.java` (`POST /api/v1/clinics/{clinicId}/walk-ins`, 201). Confirm the staff realm `SecurityConfig` requires authentication for the path (add an explicit matcher like 061/062). T015 goes green.
- [x] T022 [US1] Session list: add `walkInsWaiting`/`inWithDoctor` to `scheduling/dto/SessionSummaryResponse.java`, filled from one new grouped query in `SlotRepository` (per session: count of untimed BOOKED slots with an ACTIVE WALK_IN booking; whether any slot is APPEARED) in `ClinicSessionListController`. T017 goes green.
- [x] T023 [US1] Frontend `frontend/src/features/front-desk-walk-in/`:
  - `visitReasons.ts` (labels);
  - `api.ts` (register + error messages);
  - `PatientStep.tsx` (reuses `PatientPicker`; the new-patient form with an email field; a phone-match lookup via the existing patient search);
  - `VisitReasonStep.tsx`;
  - `SessionStep.tsx` (today's sessions via the existing `listSessions` + readiness + compact live status via the existing live-status client; hides sessions whose scheduled end has passed unless someone is in with the doctor or waiting, per the spec's "Session over" edge case, analyze C1);
  - `FrontDeskWalkInPage.tsx` (stepper, confirm, result, duplicate prompt).

  Extend `frontend/src/features/day-sheet/api.ts` session types with the new fields. T018 goes green.
- [x] T024 [US1] Routing: `/staff/clinics/:clinicId/walk-in` (optional `?sessionId=`) in `App.tsx` + `routes/staff/ClinicToolPages.tsx`; a "Walk-in" entry in the staff navigation (`routes/staff/StaffShell.tsx` or the clinic tools nav, wherever the other clinic tools are listed).

**Checkpoint**: front-desk registration works for both modes.

---

## Phase 4: User Story 2 - Send a waiting walk-in in when the doctor is free (Priority: P1)

**Goal**: FR-012–FR-015 (Fixed-Time, per the spec's plan-time clarification). **Independent Test**: quickstart.md Scenarios 5, 6, 9.

- [x] T025 [P] [US2] Frontend tests `frontend/tests/front-desk-walk-in/WalkInLinePanel.test.tsx`:
  - the waiting walk-ins list in token order, the first marked "Next";
  - "Doctor free now" when no slot is APPEARED, "Doctor busy" otherwise;
  - "Send in" calls the existing appeared endpoint for that slot and refreshes, and stays enabled while busy;
  - "Complete" on the in-with-doctor walk-in calls the existing complete endpoint;
  - "Remove" confirms, then calls the existing staff cancel endpoint.
- [x] T026 [P] [US2] Integration test `backend/src/test/java/com/cms/booking/integration/WalkInLineLifecycleTest.java`, through the real endpoints:
  - register W1/W2;
  - appeared on W1 → APPEARED with `appearedAt`;
  - complete → COMPLETED with `completedAt`;
  - staff cancel W2 → the booking is CANCELLED, not a no-show, no waitlist entry OFFERED, the slot never listed.
- [x] T027 [US2] `frontend/src/features/front-desk-walk-in/WalkInLinePanel.tsx`: reads the existing day-sheet endpoint for the selected Fixed-Time session and reuses the existing appeared/complete/cancel API clients (`features/session-delay`/`day-sheet`/`booking-cancellation`), polling at the existing ~20 s cadence. Shown on the front-desk page for the selected Fixed-Time session. T025 goes green.

**Checkpoint**: the walk-in line can be worked end to end.

---

## Phase 5: User Story 3 - Walk-ins are recognisable everywhere (Priority: P2)

**Goal**: FR-010, FR-019. **Independent Test**: quickstart.md Scenarios 1, 7.

- [x] T028 [P] [US3] Contract/unit test for `SessionDaySheetResponse` mapping (extend the existing day-sheet controller test): `SlotDetail` carries `appearedAt`/`completedAt`; `BookingDetail` carries `visitReason`/`visitReasonDetail`.
- [x] T029 [P] [US3] Frontend test in `frontend/tests/day-sheet/` for `SessionSlotsView`: Fixed-Time walk-ins (untimed + walk-in) render in a separate "Walk-in line" section with badge and visit reason, not in the timed list; Queue walk-ins show the badge and reason inline.
- [x] T030 [US3] Extend `booking/dto/SessionDaySheetResponse.java` (T028 green), and `frontend/src/features/day-sheet/SessionSlotsView.tsx` + `api.ts` types (T029 green).

---

## Phase 6: User Story 4 - Start a walk-in from a doctor's Day Sheet (Priority: P3)

**Goal**: FR-020. **Independent Test**: quickstart.md Scenario 8.

- [x] T031 [P] [US4] Frontend test: the Day Sheet walk-in button (both modes) links to `/staff/clinics/:clinicId/walk-in?sessionId=…`; the front-desk page pre-selects that session; the old `sessions/:sessionId/walk-in` route redirects there.
- [x] T032 [US4] Update the `SessionSlotsView.tsx` buttons, `FrontDeskWalkInPage` pre-selection, and the old-route redirect in `App.tsx`/`ClinicToolPages.tsx`. T031 goes green.

---

## Phase 7: Retire the old walk-in insertion (FR-016)

- [x] T033 Rewrite `backend/src/test/java/com/cms/inbox/integration/AbstractInboxIntegrationTest.insertWalkIn` to drive `FrontDeskWalkInService`. Update the walk-in cases in `booking/unit/RejectedClinicBookingRefusalTest.java` and `booking/integration/RejectedClinicBookingRefusalTest.java` (062) to use the new service/endpoint. Confirm the rejected-clinic check exists in `FrontDeskWalkInService` (test first).
- [x] T034 Delete `WalkInInsertionService`, `WalkInInsertionController`, `WalkInRequest`, the exceptions used only by them (grep-verify each), `AbstractWalkInIntegrationTest` and the 025 `WalkIn*` integration tests. Delete `frontend/src/features/staff-booking/WalkInForm.tsx`, its test and its `insertWalkIn` client, and the old `WalkInPage` (now a redirect). Grep: no remaining reference to the removed classes, the endpoint path, or the "buffer" walk-in wording.

---

## Phase 8: Polish & Cross-Cutting

- [x] T035 [P] Backend `spotlessApply`; full unit + contract suite green; all integration tests compile.
- [x] T036 [P] Frontend `npx tsc -b`, `npm run lint` (no new findings in touched files), `npx vitest run` (full suite green).
- [x] T037 Grep-verify FR-011 (no new queue entity or table), FR-012 (the free/busy hint derives only from slot APPEARED status), and FR-021 (every new query is clinic- or session-scoped).
- [x] T038 Live-verify quickstart.md Scenarios 1–9 on the restarted dev stack with throwaway data. Record the results here, then clean up the throwaway data.
  - **Results (2026-09-24, throwaway clinic "Walk-in Verify 1790264310")**:
    - **S1**: a new patient with email registered into a Fixed-Time session → W1, position 1, fee 450 locked. Stored as WALK_IN / PAIN with the email; the slot is untimed and BOOKED. All 14 timed slots unchanged (SC-003). One inbox WALK_IN item created.
    - **S2**: Other without text → 400 `VISIT_REASON_DETAIL_REQUIRED`; with text → W2, position 2. Duplicate → 409 `DUPLICATE_WALK_IN`; confirmed → W3.
    - **S4**: the session list shows Fixed-Time booked 0/14 (walk-ins excluded from capacity), 3 waiting, doctor free; Queue 0 waiting.
    - **S5**: Send in W1 → 200, `appeared_at` set; the list shows 2 waiting, in with doctor. Complete → 200, `completed_at` set.
    - **S6**: Remove W2 → booking CANCELLED, slot OPEN but untimed, so it can never be listed.
    - **S7**: a Queue walk-in → QUEUE token 1, no position, source WALK_IN. The inbox has 4 WALK_IN items in total.
    - **S8**: the old `/sessions/:id/walk-in` URL redirected to `/walk-in?sessionId=…` with the session pre-selected. The Day Sheet shows a separate Walk-in line (W1 Completed / W3 Waiting, with reasons) and "Register walk-in" / "Open front desk" links.
    - **S9**: no backend errors while the minute sweeps ran over waiting and completed walk-ins. The full 10-minute grace period wasn't waited out live; that case is covered by the query filter and `UntimedSlotSweepsTest`.
    - **UI fix found during the live check**: at a 1024px viewport the two-column layout squeezed the form to about 300px. The page now goes side by side only at `xl`.
    - **Not run here**: the Testcontainers integration classes (Docker Desktop isn't running); they compile.

---

## Dependencies & Execution Order

- **Order**: T001–T002 → Phase 2 (blocks all) → US1 (MVP) → US2 (needs US1's registration) → US3 ∥ US4 → Phase 7 (after US1, since it rewires fixtures to the new service) → Polish.
- **Tests before implementation** within each phase; [P] tasks touch different files.
- **Serialization**: T011 touches 6 files, so it runs sequentially after T005/T006.

## Parallel Opportunities

- T003–T007 in parallel; T014–T018 in parallel; T025 ∥ T026; T028 ∥ T029 ∥ T031; T035 ∥ T036.

## Implementation Strategy

- **MVP**: Phases 1–3. The front-desk screen registers walk-ins safely in both modes.
- **Next**: US2 (working the line) → US3 (visibility) → US4 (shortcut) → retire the old flow → polish and live verification.

## Phase 9: Convergence

- [x] T039 Test first (extend `backend/src/test/java/com/cms/patient/record/integration/PatientAnonymizationTest.java` with a stale past Fixed-Time walk-in: untimed `BOOKED` slot in yesterday's session → anonymization succeeds; today's waiting walk-in → blocked). Then, in `backend/src/main/java/com/cms/booking/repository/BookingRepository.java`, change `existsActiveFutureBookingForPatient` (037) and the two 008 cascade queries (`findActiveFutureBookingsByClinic`, `findActiveFutureBookingsByDoctor`) so an untimed slot (queue token or walk-in line place) counts as pending only when its session is today or later, while timed `BOOKED` slots keep today's rule. Per spec edge case "Walk-ins still waiting at the end of the day" and 008's past-bookings-untouched rule (contradicts)
