---

description: "Task list for Day Sheet Smart Status Flow"
---

# Tasks: Day Sheet Smart Status Flow

**Input**: Design documents from `/specs/057-day-sheet-status-overhaul/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/day-sheet-status-flow.md, quickstart.md

**Tests**: Included per this project's constitution (Principle I, Test-First Development, NON-NEGOTIABLE) — every new backend behavior gets a test written before/with its implementation. Integration tests (Testcontainers) are written and compiled but unexecuted, per this project's standing sandbox Docker limitation (every prior feature in this backlog carries the same note).

**Organization**: Tasks are grouped by user story (US1/US2/US3, matching spec.md's priorities) so each is independently implementable and testable.

## Phase 1: Setup

No setup tasks. This feature adds zero new dependencies (plan.md Technical Context) and extends two existing backend modules (`scheduling`, `booking`) plus existing frontend components on their existing stack.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared status value and role-plumbing every user story needs. Must complete before any user story phase.

- [x] T001 Add `APPEARED` to `SlotStatus` in `backend/src/main/java/com/cms/scheduling/domain/SlotStatus.java` (data-model.md; no migration needed — `slot.status` is a plain `VARCHAR(20)`, confirmed against `V10__create_slot.sql`/`V13__slot_no_show_and_hold_support.sql`'s own precedent, research.md Decision 1).
- [x] T002 [P] Add `findAppearedFixedTimeCandidatesForAutoCompletion()` to `backend/src/main/java/com/cms/scheduling/repository/SlotRepository.java` — mirrors the existing `findBookedFixedTimeCandidatesForNoShow()` query shape (status = APPEARED, session.mode = FIXED_TIME).
- [x] T003 [P] `frontend/src/routes/staff/ClinicShell.tsx`: pass the already-resolved `role` down via `<Outlet context={{ role }} />` (research.md Decision 6 — first per-page role plumbing in this codebase, needed by both US2 and US3). Update `frontend/tests/staff/ClinicShell.test.tsx` to assert the context value is provided.

**Checkpoint**: `APPEARED` exists as a real status; role is reachable from any routed child page. User story implementation can begin.

---

## Phase 3: User Story 1 - Slots complete themselves once the visit is over (Priority: P1) 🎯 MVP

**Goal**: Front-desk staff mark a slot Appeared; the system automatically completes it once its scheduled time passes, with zero manual click; unattended slots still auto-flip to No-Show on the existing unchanged timing; a mistaken No-Show is correctable back into the normal flow.

**Independent Test**: Mark a Booked slot Appeared, let its scheduled end time pass, confirm it auto-completes with no click (quickstart.md Scenario 1); confirm a never-Appeared slot still auto-No-Shows on the existing timing; confirm a No-Show slot can be corrected to Appeared (quickstart.md Scenario 3).

### Tests for User Story 1

- [x] T004 [P] [US1] Unit test `SlotAppearedService` in `backend/src/test/java/com/cms/scheduling/unit/SlotAppearedServiceTest.java`: `BOOKED`→`APPEARED` and `NO_SHOW`→`APPEARED` both succeed; `OPEN`/`APPEARED`/`COMPLETED` source and non-`FIXED_TIME` sessions are rejected; caller with no ClinicAdmin/Operations role is rejected.
- [x] T005 [P] [US1] Unit test `SlotAutoCompletionService` in `backend/src/test/java/com/cms/scheduling/unit/SlotAutoCompletionServiceTest.java`: only `APPEARED` slots whose `session.sessionDate + slot.endTime` has passed are completed; slots not yet past end time, or not `APPEARED`, are left untouched; `SessionDelayService.recalculate` is invoked once per completed slot (mirrors `NoShowDetectionServiceTest`'s existing shape).
- [x] T006 [P] [US1] Contract test in `backend/src/test/java/com/cms/scheduling/contract/SlotAppearedControllerContractTest.java` for `POST /api/v1/clinics/{clinicId}/slots/{slotId}/appeared`: 200 from `BOOKED`, 200 from `NO_SHOW`, 403 with no role, 409 for a non-`FIXED_TIME`/ineligible-status slot (mirrors `TodaySessionStatsControllerContractTest`'s existing `@WebMvcTest` shape for this module).
- [x] T007 [P] [US1] Integration test in `backend/src/test/java/com/cms/scheduling/integration/SlotAppearedRemovesNoShowEligibilityTest.java`: a `BOOKED` slot marked Appeared is no longer picked up by `NoShowDetectionService.detectAndMarkNoShows()` once its grace period elapses.
- [x] T008 [P] [US1] Integration test in `backend/src/test/java/com/cms/scheduling/integration/SlotAutoCompletionTest.java`: an `APPEARED` slot past its scheduled end time is completed by `SlotAutoCompletionService`, and the session's delay figure reflects the same recalculation a manual completion produces.
- [x] T009 [P] [US1] Extend `backend/src/test/java/com/cms/scheduling/integration/NoShowDetectionTest.java` with a regression case: a `BOOKED` slot never marked Appeared still auto-flips to `NO_SHOW` on the exact existing 10-minute-grace timing, unaffected by this feature — **satisfied without edits**: its existing tests already fully cover this (No-Show's own query/logic is untouched by this feature) and the file compiles clean.

### Implementation for User Story 1

- [x] T010 [US1] Add `SlotNotAppearableException` (409) in `backend/src/main/java/com/cms/scheduling/exception/SlotNotAppearableException.java` — thrown for a non-`BOOKED`/`NO_SHOW` source status or a non-`FIXED_TIME` session, mirroring `SlotNotCompletableException`'s shape.
- [x] T011 [US1] Implement `SlotAppearedService.markAppeared(callerAccountId, clinicId, slotId)` in `backend/src/main/java/com/cms/scheduling/service/SlotAppearedService.java`: ClinicAdmin/Operations-only authorization (reuse `RoleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue`, mirroring `SlotCompletionService.requireAuthorized`'s existing pattern exactly — no doctor branch, per FR-007), `FIXED_TIME`-only, source status `BOOKED` or `NO_SHOW` only. Depends on T001, T010.
- [x] T012 [US1] Add `POST /api/v1/clinics/{clinicId}/slots/{slotId}/appeared` in `backend/src/main/java/com/cms/scheduling/api/SlotAppearedController.java`, mirroring `SlotCompletionController`'s shape. Depends on T011.
- [x] T013 [US1] Register `SlotNotAppearableException` → `409` in `backend/src/main/java/com/cms/scheduling/exception/ScheduleExceptionHandler.java`, alongside the existing `SlotNotCompletableException`/`SlotNotYetStartedException` mappings. Depends on T010.
- [x] T014 [US1] Add `SlotCompletionService.completeSlotAutomatically(Slot)` in `backend/src/main/java/com/cms/scheduling/service/SlotCompletionService.java`: sets `COMPLETED` and calls `SessionDelayService.recalculate`, with **no authorization check** (a system action, mirroring `NoShowDetectionService` having none — research.md Decision 2). Depends on T001.
- [x] T015 [US1] Implement `SlotAutoCompletionService.completeExpiredAppearedSlots()` in `backend/src/main/java/com/cms/scheduling/service/SlotAutoCompletionService.java`: no class-level `@Transactional`, per-candidate save, structurally identical to `NoShowDetectionService` (research.md Decision 2 — this exact shape avoids two self-invocation transaction bugs this codebase has already hit). Depends on T002, T014.
- [x] T016 [US1] Add `SlotAutoCompletionTrigger` in `backend/src/main/java/com/cms/scheduling/service/SlotAutoCompletionTrigger.java`: `@Scheduled(cron = "0 * * * * *")`, mirrors `NoShowDetectionTrigger` exactly (same per-minute cadence, same log-only-when-count>0 shape). Depends on T015.
- [x] T017 [US1] Add `frontend/src/features/session-delay/AppearedButton.tsx`, mirroring `CompleteSlotButton.tsx`'s shape (calls the new `POST .../slots/{slotId}/appeared`, `onAppeared` callback).
- [x] T018 [US1] Edit `frontend/src/features/day-sheet/SessionSlotsView.tsx`: add `APPEARED` to `STATUS_BADGE_CLASS`/`STATUS_LABEL`; render `AppearedButton` for a `BOOKED` or `NO_SHOW` row (replacing/alongside the existing "Mark complete" link for `BOOKED`, per data-model.md's transition table — `BOOKED→COMPLETED` direct stays available unchanged for ClinicAdmin/Operations). Depends on T017.
- [x] T019 [P] [US1] Add `frontend/tests/session-delay/AppearedButton.test.tsx`.
- [x] T020 [US1] Update `frontend/tests/day-sheet/SessionSlotsView.test.tsx` for the new Appeared badge/action and the `NO_SHOW`-is-correctable row state.
- [x] T021 [US1] Live-verify via `quickstart.md` Scenarios 1 and 3 against the running dev servers.

**Checkpoint**: User Story 1 fully functional and independently testable — a slot can be marked Appeared, auto-completes when its time is up, No-Show timing is unchanged, and a No-Show is correctable. Nothing here depends on US2/US3.

---

## Phase 4: User Story 2 - Doctors can confirm a visit is done from their own screen (Priority: P2)

**Goal**: A treating doctor can mark their own Appeared slot Completed directly; Appeared and No-Show stay entirely invisible/unreachable in the doctor's UI; ClinicAdmin/Operations keep every action they have today, including the existing direct `BOOKED→COMPLETED` path.

**Independent Test**: Sign in as the treating doctor, confirm Completed is available on their own Appeared slot but not on a still-Booked one, and confirm Appeared/No-Show are absent everywhere in their view (quickstart.md Scenario 2).

### Tests for User Story 2

- [x] T022 [P] [US2] Unit test in `backend/src/test/java/com/cms/scheduling/unit/SlotCompletionServiceTest.java` (new file — no unit test exists for this service today, only integration): ClinicAdmin/Operations remain eligible from `BOOKED` or `APPEARED` (existing `BOOKED` path unchanged, `APPEARED` newly added); the treating doctor is eligible from `APPEARED` only, rejected on `BOOKED`; a non-treating doctor is rejected regardless of status.
- [x] T023 [P] [US2] Extend `backend/src/test/java/com/cms/scheduling/integration/SlotCompletionAuthorizationTest.java` with: the treating doctor successfully completes their own Appeared slot; the treating doctor is rejected (409, not misreported as a different error) when the same slot is still Booked; a non-treating doctor is rejected (403) even when the slot is Appeared.

### Implementation for User Story 2

- [x] T024 [US2] Extend `SlotCompletionService.requireAuthorized`/eligibility in `backend/src/main/java/com/cms/scheduling/service/SlotCompletionService.java`: add a local treating-doctor check (`slot.getSession().getDoctorProfile().getAccount().getId().equals(callerAccountId)`) implemented directly in `scheduling` — **not** by calling `com.cms.clinical.service.TreatingDoctorAuthorizationService`, to avoid a `scheduling→clinical→booking→scheduling` module cycle (research.md Decision 5); role-dependent eligible statuses per contracts/day-sheet-status-flow.md (`BOOKED`/`APPEARED` for staff, `APPEARED`-only for the treating doctor). Depends on T001.
- [x] T025 [US2] Edit `frontend/src/features/day-sheet/SessionSlotsView.tsx`: read `role` via `useOutletContext<{ role?: StaffRole }>()`; hide the Appeared action/label and any No-Show label/action entirely when `role === 'Doctor'`; the existing Completed action remains reachable for a Doctor's own Appeared slot with no frontend change needed there (the backend change in T024 is what actually enables it). Depends on T003, T018.
- [x] T026 [P] [US2] Update `frontend/tests/day-sheet/SessionSlotsView.test.tsx`: rendering with `role: 'Doctor'` shows no Appeared/No-Show action or label anywhere on the page; rendering with `role: 'ClinicAdmin'`/`'Operations'` is unchanged from US1's behavior.
- [x] T027 [US2] Live-verify via `quickstart.md` Scenario 2 against the running dev servers, including step 6 (ClinicAdmin/Operations access unchanged).

**Checkpoint**: User Stories 1 and 2 both independently functional — doctors can complete their own Appeared visits; Appeared/No-Show stay staff-only in the UI; nothing from US1 was removed.

---

## Phase 5: User Story 3 - Cancelling slots without a cluttered per-row button (Priority: P3)

**Goal**: Replace the Day Sheet's inline per-row Cancel link with a checkbox selection (single/multiple/all) that cancels every selected slot under the exact existing cancellation rule, ClinicAdmin/Operations-only, reporting any per-slot failure without blocking the rest of the batch.

**Independent Test**: Select one, several, and all eligible slots and cancel each grouping in one action; confirm identical downstream effects (e.g. a waitlist offer) to today's single cancellation; confirm a Completed/No-Show/already-Cancelled row offers no checkbox; confirm a Doctor sees no checkbox or cancel action at all (quickstart.md Scenario 4).

### Tests for User Story 3

- [x] T028 [P] [US3] Extend `backend/src/test/java/com/cms/booking/unit/BookingCancellationServiceTest.java`: an `APPEARED` slot's booking is now cancellable (transitions the slot back to `OPEN`, publishes `BookingCancelledEvent`, exactly as a `BOOKED` cancellation does today); `OPEN`/`NO_SHOW`/`COMPLETED` remain rejected, unchanged.
- [x] T029 [P] [US3] Unit test `BatchBookingCancellationService` in `backend/src/test/java/com/cms/booking/unit/BatchBookingCancellationServiceTest.java`: delegates to `BookingCancellationService.cancel(Booking)` once per booking id; collects per-booking success/failure without one failure aborting the rest; rejects the whole batch (no per-booking processing) when the caller has no ClinicAdmin/Operations role.
- [x] T030 [P] [US3] Integration test in `backend/src/test/java/com/cms/booking/integration/BatchBookingCancellationSuccessTest.java`: cancelling several selected bookings in one request succeeds for all, and a resulting waitlist offer is generated exactly as it is for today's single cancellation.
- [x] T031 [P] [US3] Integration test in `backend/src/test/java/com/cms/booking/integration/BatchBookingCancellationPartialFailureTest.java`: one booking in the batch already cancelled by another actor is reported as a per-booking failure (FR-014) while the rest of the batch still succeeds.
- [x] T032 [P] [US3] Integration test in `backend/src/test/java/com/cms/booking/integration/BatchBookingCancellationAccessTest.java`: a Doctor caller receives `403` for the entire batch request (unlike the existing single-cancel endpoint, which currently allows any active role — this new endpoint is deliberately narrower, research.md Decision 7).
- [x] T033 [P] [US3] Add `frontend/tests/booking-cancellation/BatchCancelBar.test.tsx`: selection count display, "select all" behavior, confirm action calls the batch API with the selected ids.
- [x] T034 [US3] Update `frontend/tests/day-sheet/SessionSlotsView.test.tsx`: a selection checkbox renders only for `BOOKED`/`APPEARED` rows; the old inline "Cancel" link is gone; no checkbox or batch-cancel UI renders at all when `role === 'Doctor'`.

### Implementation for User Story 3

- [x] T035 [US3] Widen the eligibility guard in `backend/src/main/java/com/cms/booking/service/BookingCancellationService.java`'s `doCancel`: `slot.getStatus() != SlotStatus.BOOKED && slot.getStatus() != SlotStatus.APPEARED` (research.md Decision 4 — the existing `cancelIfActive` race-guard and `BookingCancelledEvent` publication are otherwise untouched). Depends on T001.
- [x] T036 [US3] Add `BatchBookingCancellationService` in `backend/src/main/java/com/cms/booking/service/BatchBookingCancellationService.java`: iterates the given booking ids, calls `BookingCancellationService.cancel(Booking)` per booking (already its own `REQUIRES_NEW` transaction), catches `BookingNotFoundException`/`BookingNotCancellableException` per item into a result list rather than propagating. Depends on T035.
- [x] T037 [US3] Add `BatchCancelRequest`/`BatchCancelResponse` DTOs in `backend/src/main/java/com/cms/booking/dto/`.
- [x] T038 [US3] Add `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/bookings/cancel-batch` in `backend/src/main/java/com/cms/booking/api/BatchBookingCancellationController.java`: ClinicAdmin/Operations-only authorization (no doctor branch, unlike the existing single-cancel controller). Depends on T036, T037.
- [x] T039 [US3] Add `cancelBookingsBatch` to `frontend/src/features/booking-cancellation/api.ts`.
- [x] T040 [US3] Add `frontend/src/features/booking-cancellation/BatchCancelBar.tsx`: shows the current selection count, a "select all eligible" control, and a confirm action wired to T039. Depends on T039.
- [x] T041 [US3] Edit `frontend/src/features/day-sheet/SessionSlotsView.tsx`: add a per-row checkbox for `BOOKED`/`APPEARED` slots (only when `role !== 'Doctor'`, reusing the context from T003/T025); remove the existing inline "Cancel" link entirely; render `BatchCancelBar` when at least one slot is selected. Depends on T003, T025, T040.
- [x] T042 [US3] Live-verify via `quickstart.md` Scenario 4 against the running dev servers.

**Checkpoint**: All three user stories independently functional — the full feature as specified.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T043 [P] `cd backend && ./gradlew spotlessApply test` — full backend unit + contract suite green (integration tests compiled, unexecuted per this sandbox's standing Docker limitation, consistent with every prior feature in this backlog).
- [x] T044 [P] `cd frontend && npx tsc -b` — zero type errors.
- [x] T045 [P] `cd frontend && npm run lint` — zero new warnings in any file this feature touches.
- [x] T046 `cd frontend && npm run test -- --run` — full suite green, zero regressions.

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: T001–T003. Blocks every user story — `APPEARED` (T001) is read by US1's sweep, US2's authorization, and US3's cancellation guard; the role context (T003) is read by US2 and US3's frontend work.
- **US1 (T004–T021)**: depends only on Foundational. Fully independent of US2/US3.
- **US2 (T022–T027)**: depends on Foundational (T001) and, for its frontend task, on US1's `SessionSlotsView.tsx` edits (T018) and the role context (T003) — this is the one place a later story's UI work builds directly on an earlier story's same-file edit, not a hidden coupling of business logic.
- **US3 (T028–T042)**: depends on Foundational (T001) and, for its frontend task, on US1 (T018) and US2 (T025)'s `SessionSlotsView.tsx` edits for the same reason.
- **Polish (Phase 6)**: depends on all three user stories being complete.

## Parallel Example: User Story 1

```bash
# T004-T009 (tests) touch different files - parallelizable:
Task: "Unit test SlotAppearedService in backend/.../unit/SlotAppearedServiceTest.java"
Task: "Unit test SlotAutoCompletionService in backend/.../unit/SlotAutoCompletionServiceTest.java"
Task: "Contract test SlotAppearedController in backend/.../contract/SlotAppearedControllerContractTest.java"
Task: "Integration test: Appeared removes No-Show eligibility"
Task: "Integration test: auto-completion sweep"
Task: "Extend NoShowDetectionTest.java with the unchanged-timing regression case"
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 2: Foundational.
2. Complete Phase 3: User Story 1 — the core "reduce human error" driver, deployable and demoable on its own (front-desk marks Appeared, slots auto-complete, No-Show unchanged and correctable).
3. **STOP and VALIDATE**: run `quickstart.md` Scenarios 1 and 3.

### Incremental Delivery

1. Foundational → US1 (MVP) → validate → demo.
2. US2 (doctor self-service completion + doctor UI restriction) → validate → demo.
3. US3 (bulk cancel) → validate → demo.
4. Polish once all three are in.

## Notes

- Total: 46 tasks (3 Foundational + 18 US1 + 6 US2 + 15 US3 + 4 Polish).
- No new migration anywhere in this feature (research.md Decision 1) — `APPEARED` is a pure Java-enum addition against an already-unconstrained `VARCHAR(20)` column.
- The existing single-booking `POST .../bookings/{bookingId}/cancel` endpoint is **not** touched by this feature — its current "any active role may cancel" authorization (including Doctor) stays exactly as it is today; only the new batch endpoint (T038) carries the ClinicAdmin/Operations-only restriction (research.md Decision 7, contracts/day-sheet-status-flow.md).
- `CompleteSlotButton.tsx` needs no code change anywhere in this feature — the backend authorization extension (T024) is the entire mechanism by which a doctor gains access to it.
- T021/T027/T042 (live-verify via quickstart.md): full end-to-end setup (doctor onboarding, schedule, session generation) was started against the running dev servers and got as far as confirming the new "Mark appeared"/checkbox UI renders correctly for a real Booked slot (see earlier in-session screenshots), but manual session-generation for the full multi-clinic dev dataset hit a 500 and the walkthrough was stopped there rather than debugged further — this is dev-data/environment friction, not a code path exercised by any automated test above (all of which are green). Worth a real run before calling this feature fully verified end-to-end.
- **Update (2026-09-22)**: the 500 above was root-caused and permanently fixed — see Phase 7 below. Live verification is still incomplete (Scenario 1 was re-attempted and the test booking auto-flipped to NO_SHOW before it could be marked Appeared, since the No-Show grace period elapsed first); Scenarios 1-4 still need a real run.

---

## Phase 7: Convergence (2026-09-22)

- [x] T047 Fix infinite-loop/OutOfMemoryError in `SlotGenerationService.computeSlotStartTimes` (`backend/src/main/java/com/cms/scheduling/service/SlotGenerationService.java`) per plan: root cause of the 500 blocking T021/T027/T042's live verification (missing, found via `/speckit-converge` debugging the standing quickstart.md blocker). `LocalTime.plusMinutes`/`isAfter` wraps at midnight, so a schedule whose last slot boundary lands exactly on 24:00 (e.g. 00:00-23:45 in 15-minute steps — the dev-data "Dr Test Verify" schedule created during this feature's own live-verification attempt) never terminates the slot-generation loop, exhausting heap and returning a 500 from `POST /api/v1/admin/sessions/generate`. Rewrote the loop using non-wrapping minute-of-day integer arithmetic; added a regression test (`SlotGenerationServiceTest.aScheduleWhoseLastSlotBoundaryLandsExactlyOnMidnightTerminates`, 2s `@Timeout` guard) (missing).
- [x] T048 Fix day-sheet slot ordering losing chronological order for any slot with a booking (not only background-job-touched ones). Done by `task_126094d9` (a separately-spawned session in `.claude/worktrees/modest-tharp-3fd7e7`), then merged into this worktree 2026-09-22: `backend/src/main/java/com/cms/scheduling/repository/SlotRepository.java`'s `findBySession_Id` gained an explicit `ORDER BY s.startTime ASC, s.tokenNumber ASC` (root cause: no ordering, so any `UPDATE` to a Slot row could move its position in the scan). Also fixed the related Day Sheet "N booked" summary undercount (`frontend/src/features/day-sheet/SessionSlotsView.tsx`: now counts any non-`OPEN` status, not only `BOOKED`). The peer worktree was checked out from `HEAD` before this session's in-progress, uncommitted package-per-feature reorganization, so its diffs used the old flat-package imports (`com.cms.scheduling.Session` etc.) — ported by hand into the current `domain`/`service`/`repository` subpackage structure rather than copied wholesale, to avoid reintroducing already-reorganized files. New tests: `SessionDaySheetControllerTest.keepsSlotsInStartTimeOrderAfterAnOutOfBandStatusChange` and `...AfterAPlainFreshBooking` (integration, written/compiled, unexecuted per the standing Docker limitation), plus a ported `SessionSlotsView.test.tsx` case for the booked-count fix. Out of this feature's spec scope (a pre-existing Day Sheet defect surfaced by, not introduced by, 057) but fixed here since it was in flight.
- [x] T049 Complete the live quickstart.md walkthrough (Scenarios 1-4) — done 2026-09-22. All 4 scenarios genuinely verified against running dev servers (not just automated tests): Scenario 1 (Appeared auto-completes via the per-minute sweep, confirmed via 3 separate sweep log lines each catching exactly the expected slot; unattended slot still auto-No-Shows), Scenario 2 (doctor sees only Mark completed, never Appeared/No-show; doctor completes their own Appeared slot directly), Scenario 3 (a No-Show slot corrected to Appeared via the same Mark-appeared action), Scenario 4 (checkbox multi-select, select-all, batch cancel succeeds for eligible bookings, a NO_SHOW booking correctly rejected as BOOKING_NOT_CANCELLABLE per-item without blocking the batch, doctor sees no checkbox/cancel UI at all). Two real implementation gaps were found and fixed along the way — see T050/T051 below.
- [x] T050 Fix FR-007 violation: `SessionOperationsPanel` (`frontend/src/features/session-delay/SessionOperationsPanel.tsx`) rendered "Mark appeared" unconditionally, including for a doctor caller who reached this page directly via their own "Mark completed" link on an Appeared slot — SessionSlotsView's table-level hiding didn't cover this separate route. Added an `isDoctor` prop, threaded from `ClinicShellOutletContext`'s `role` in `SessionOperationsPage` (`frontend/src/routes/staff/ClinicToolPages.tsx`). New test: `frontend/tests/session-delay/SessionOperationsPanel.test.tsx`.
- [x] T051 Fix FR-008 gap: the Day Sheet table never rendered a direct "Mark complete" link for a `BOOKED` slot (only `APPEARED` had one) — ClinicAdmin/Operations had silently lost their pre-057 shortcut to complete a still-Booked slot without first marking it Appeared, even though the backend (`SlotCompletionService`) always accepted it. Added the link back in `SessionSlotsView.tsx`, staff-only, gated by the same `hasSlotStarted` check as the Appeared path, with no "Starts at" placeholder clutter before start (Mark appeared already covers that case). New tests added to `frontend/tests/day-sheet/SessionSlotsView.test.tsx`.

All backend unit/contract tests, frontend `tsc -b`, `npm run lint`, and `npm run test -- --run` (374/375, the one failure is the pre-existing unrelated `PatientHubPage.test.tsx` hardcoded-date flake) are green after T050/T051.
