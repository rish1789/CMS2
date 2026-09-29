---
description: "Task list for 064-queue-send-in-complete"
---

# Tasks: Send-In and Complete for Queue Sessions

**Input**: `specs/064-queue-send-in-complete/` (spec, plan, research, data-model, contracts, quickstart)

**Tests**: REQUIRED (Constitution I). Each change follows its failing test. Tests that assert the old Fixed-Time-only rule for queue sessions are updated to the new rule in the same task, with a note citing spec 064.

**Environment**: Gradle via `"/c/Users/risha/AppData/Local/Temp/gradle-8.10/bin/gradle.bat" -p backend …`. Stop the backend and `rm -rf backend/build` on "not a regular file". Integration tests are Docker-gated here.

## Phase 1: Setup

- [X] T001 Integration test `backend/src/test/java/com/cms/booking/integration/QueueTokenMigrationTest.java` (Constitution I, data migration): rows shaped like pre-064 data (an active queue booking whose token is `OPEN`, and a cancelled one) → after re-running V40's statement, the active one is `BOOKED` and the cancelled one stays `OPEN`. Then create `backend/src/main/resources/db/migration/V40__queue_tokens_booked.sql` per data-model.md.

## Phase 2: Foundational — tokens are minted waiting (FR-001)

- [X] T002 Update `backend/src/test/java/com/cms/scheduling/unit/QueueSlotServiceWalkInTest.java`: both `issueNextSlot` (QUEUE) and `issueNextWalkInSlot` (FIXED_TIME) return a `BOOKED` untimed token. Confirm red.
- [X] T003 `QueueSlotService`: the shared issuance core mints the token `BOOKED` (research.md Decision 1). Remove the now-redundant `slot.setStatus(SlotStatus.BOOKED)` in `FrontDeskWalkInService`. T002 goes green; `FrontDeskWalkInServiceTest` stays green.

## Phase 3: User Story 1 - Staff send queue patients in and complete their visits (Priority: P1) 🎯 MVP

- [X] T004 [P] [US1] Update `backend/src/test/java/com/cms/scheduling/unit/SlotAppearedServiceTest.java`: its QUEUE case now expects success (`APPEARED` with `appearedAt`) instead of `NotAFixedTimeSessionException`.
- [X] T005 [P] [US1] Add to `backend/src/test/java/com/cms/scheduling/unit/SlotCompletionServiceTest.java`: staff complete a waiting or in-with-doctor queue token; the treating doctor completes an in-with-doctor queue token; `completedAt` is stamped.
- [X] T006 [P] [US1] Update `backend/src/test/java/com/cms/scheduling/integration/SlotCompletionRejectionTest.java`: any queue-session rejection case becomes an acceptance case.
- [X] T007 [US1] Remove the Fixed-Time-only guard from `SlotAppearedService.markAppeared` and `SlotCompletionService.completeSlot`. T004 and T005 go green.
- [X] T008 [P] [US1] Update `backend/src/test/java/com/cms/booking/unit/BookingCancellationServiceTest.java` (the QUEUE case now cancels, sets the token `OPEN` and publishes the event, which `WaitlistBumpListener` ignores for queue sessions) and `booking/integration/StaffBookingCancellationRejectionTest.java` (a queue booking is no longer rejected). Add a new `booking/contract/QueueSelfCancelContractTest.java` (analyze A1): the patient self-cancel of a queue booking is still 409 `NOT_A_FIXED_TIME_SESSION`.
- [X] T009 [US1] Remove the Fixed-Time-only guard from `BookingCancellationService.doCancel`, keeping the patient controller's own check. T008 goes green.
- [X] T010 [P] [US1] Integration test `backend/src/test/java/com/cms/booking/integration/QueueSendInCompleteTest.java`, through the real endpoints:
  - book 3 tokens (patient, staff, front desk) → all `BOOKED`;
  - appeared token 1 → `APPEARED` with `appeared_at`;
  - complete → `COMPLETED` with `completed_at`;
  - staff cancel token 2 → CANCELLED, token `OPEN`, and a WAITING waitlist entry for that doctor is not OFFERED.

- [X] T010a [P] [US1] Frontend test in `frontend/tests/day-sheet/SessionSlotsView.test.tsx` (FR-006, analyze C1): a Queue session's `BOOKED` token row shows Appeared and Complete for staff; an `APPEARED` row shows Complete; a Doctor caller sees no Appeared. No code change is expected, since the row actions depend on status alone.

## Phase 4: User Story 2 - True, shrinking queue position (Priority: P1)

- [X] T011 [P] [US2] Add to `backend/src/test/java/com/cms/booking/unit/UntimedSlotGuardsTest.java`, or a new `QueuePositionServiceTest`, for a QUEUE session:
  - positions 1–4 for four waiting tokens;
  - after token 1 is APPEARED, token 4 → 3;
  - a cancelled (`OPEN`) token ahead isn't counted;
  - own token APPEARED, COMPLETED or OPEN → not applicable.
- [X] T012 [US2] `QueuePositionService.positionOf`: not applicable unless the own token is `BOOKED` (FR-005). The counting already matches FR-004 once tokens are minted `BOOKED`. T011 goes green.
- [X] T013 [P] [US2] Integration test `backend/src/test/java/com/cms/booking/integration/QueuePositionShrinksTest.java`: through the patient and staff queue-position endpoints, positions fall as tokens ahead are sent in or cancelled.

## Phase 5: User Story 3 - The front-desk screen works for queue sessions (Priority: P2)

- [X] T014 [P] [US3] Update `backend/src/test/java/com/cms/scheduling/integration/SessionListWalkInCountsTest.java`: the Queue session now reports waiting tokens and `inWithDoctor` (replacing the 063 "always 0/false" case).
- [X] T015 [US3] `SlotRepository.countWalkInLineBySessionIdIn`: drop the `mode = FIXED_TIME` filter and update its Javadoc; the expressions are mode-independent (research.md Decision 4). Update the `SessionSummaryResponse` field comment.
- [X] T016 [P] [US3] Frontend tests:
  - `frontend/tests/front-desk-walk-in/FrontDeskWalkInPage.test.tsx`: the Queue session card shows "N waiting" and the free/busy hint; choosing it shows the waiting-line panel.
  - `frontend/tests/front-desk-walk-in/WalkInLinePanel.test.tsx`: in a Queue session the panel lists every waiting token in number order, booked and walk-in, labelled "Token n" with walk-ins badged, and Send in / Complete / Remove work.
- [X] T017 [US3] Frontend:
  - `SessionStep.tsx`: waiting count and free/busy hint for Queue sessions too ("N waiting" wording for Queue);
  - `FrontDeskWalkInPage.tsx`: show `WalkInLinePanel` for any selected session;
  - `WalkInLinePanel.tsx`: add a `mode` prop; Queue lists all waiting untimed booked tokens as "Token n" (walk-ins badged); the heading reads "waiting line" for Queue.

  T016 goes green.

## Phase 6: User Story 4 - Existing queue bookings carried over (Priority: P3)

- [X] T018 [US4] Confirm T001's migration covers FR-011. Live-check the 2 Star Clinic rows in quickstart step 8.

## Phase 7: Polish

- [X] T019 [P] Backend `spotlessApply`; full unit + contract suite green; integration tests compile.
- [X] T020 [P] Frontend `tsc -b`, lint (no new findings in touched files), full `vitest` green.
- [X] T021 Grep: no remaining Fixed-Time-only guard in Appeared/Complete/staff-cancel; patient self-cancel keeps its guard; no new queue or status entity (FR-008).
- [X] T022 Live-verify quickstart.md steps 1–9 with a throwaway clinic. Record the results here, then clean up.

  **Live results (2026-09-24, throwaway clinic "Queue Verify", purged afterwards):**
  - The front-desk walk-in, staff queue booking and patient self-service each created a token: 1, 2 and 3, all `BOOKED`.
  - Patient position: 3 at first. After token 1 was sent in, it was 2 (`appeared_at` set), and the counts read "2 waiting, busy". Token 1 was then completed, with `completed_at` set.
  - Staff cancelled token 2: the booking was CANCELLED, the token went back to `OPEN`, and the patient position became 1. No waitlist offers were made.
  - Patient self-cancel of a queue booking still returned 409 `NOT_A_FIXED_TIME_SESSION`.
  - The live status is still not applicable.
  - Once the patient's own token was sent in, the position became not applicable.
  - V40: both Star Clinic active queue tokens changed from `OPEN/ACTIVE` to `BOOKED/ACTIVE`.
  - UI coverage comes from component tests (T010a, T016). No interactive browser login was done.
  - Side effect: whole-session and cutoff session cancellation now reach real queue bookings. Before 064 they found no `BOOKED` tokens, although `SessionCancellationSuccessTest` and `PartialSessionCancellationSuccessTest` already assumed they would.

## Dependencies & Execution Order

T001 → T002–T003 (blocks all) → US1 (T004–T010) → US2 (T011–T013) → US3 (T014–T017) → US4 (T018) → Polish. [P] tasks touch different files. T007 and T009 edit separate files and can run in parallel after their tests.

## Implementation Strategy

- **MVP**: T001–T010 (tokens waiting, send in / complete / cancel).
- **Then**: position (US2), front desk (US3), migration check (US4), polish and live verification.
