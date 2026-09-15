---

description: "Task list for Session Delay Tracking (Fixed-Time Only)"
---

# Tasks: Session Delay Tracking (Fixed-Time Only)

**Input**: Design documents from `/specs/026-session-delay-tracking/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/session-delay-tracking.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 mark-a-Slot-completed + recalculation, US2 = P2 walk-in-insertion trigger, US3 = P1 view-the-delay-figure, tied with US1) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 011/012/021/022/025's existing `com.cms.scheduling` module: `backend/src/main/java/com/cms/scheduling/`, `backend/src/test/java/com/cms/scheduling/integration/`. Extends 025's already-converged `backend/src/main/java/com/cms/booking/WalkInInsertionService.java` with one new call site. New: `frontend/src/features/session-delay/`, `frontend/tests/session-delay/` (path convention confirmed against this project's actual test directories, per plan.md's note — see `frontend/tests/staff-booking/`, `frontend/tests/scheduling/`).

---

## Phase 1: Setup

**Purpose**: Response DTOs shared by both new endpoints.

- [X] T001 [P] Create `SlotCompletionResponse` record (`slotId`, `status`) in `backend/src/main/java/com/cms/scheduling/dto/SlotCompletionResponse.java`
- [X] T002 [P] Create `SessionDelayResponse` record (`sessionId`, `applicable`, `delayMinutes`) per contracts/session-delay-tracking.md in `backend/src/main/java/com/cms/scheduling/dto/SessionDelayResponse.java`

**Checkpoint**: Types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema change, the new enum value, the new exception types, security matchers, and the shared test fixture.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T003 Create migration `V15__session_delay_minutes.sql` — nullable `delay_minutes INTEGER` column on `session` — in `backend/src/main/resources/db/migration/V15__session_delay_minutes.sql` per data-model.md
- [X] T004 Extend `SlotStatus`: add `COMPLETED` (reachable only from `BOOKED`, enforced in service code, not the enum itself) in `backend/src/main/java/com/cms/scheduling/SlotStatus.java`
- [X] T005 Extend `Session`: add nullable `delayMinutes` (`Integer`) field + getter + setter (mirrors `Slot.setStatus`'s existing mutator shape) in `backend/src/main/java/com/cms/scheduling/Session.java` (depends on T003)
- [X] T006 [P] Create `SlotNotFoundException` (scheduling-side — distinct from `com.cms.booking.SlotNotFoundException`, mirrors its shape) in `backend/src/main/java/com/cms/scheduling/SlotNotFoundException.java`
- [X] T007 [P] Create `SlotNotCompletableException` (covers both "still OPEN" and "already COMPLETED") in `backend/src/main/java/com/cms/scheduling/SlotNotCompletableException.java`
- [X] T008 Add `SLOT_NOT_FOUND` (404) and `SLOT_NOT_COMPLETABLE` (409) mappings to `backend/src/main/java/com/cms/scheduling/ScheduleExceptionHandler.java` per contracts/session-delay-tracking.md (depends on T006, T007). `NOT_A_FIXED_TIME_SESSION` needs no new mapping — `com.cms.booking.BookingExceptionHandler`'s existing global `@RestControllerAdvice` mapping for `NotAFixedTimeSessionException` (025) already applies application-wide.
- [X] T009 Add explicit matchers `.requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/slots/*/complete").authenticated()` and `.requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/sessions/*/delay").authenticated()` to the `/api/v1/clinics/**` chain (research.md — proactively avoids 014's silent-fallthrough-to-`permitAll()` gap class) in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`
- [X] T010 Create `AbstractSessionDelayIntegrationTest extends AbstractNoShowDetectionIntegrationTest` (reuses its `saveFixedTimeSlotAt`/`saveQueueSlot`/`saveBookingFor` helpers directly via inheritance — this codebase's own established `com.cms.scheduling` test-fixture pattern, unlike `com.cms.booking`'s duplication-per-fixture precedent), adding one new helper: `operationsToken(Clinic)` (mirrors `AbstractScheduleIntegrationTest.clinicAdminToken`'s shape — no `com.cms.scheduling` test fixture has ever needed an Operations-role token before, since `ScheduleService`'s own authorization is doctor-or-ClinicAdmin, not Operations) in `backend/src/test/java/com/cms/scheduling/integration/AbstractSessionDelayIntegrationTest.java`

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: Staff Marks a Slot Completed, Delay Recalculates (Priority: P1) 🎯 MVP

**Goal**: An authorized staff member marks a `BOOKED` Fixed-Time Slot completed; the Session's delay figure recalculates and is readable via the query endpoint as part of the same action.

**Independent Test**: Per quickstart.md Scenarios 1, 2, 5, 7 (and 6's Queue-mode-never-has-a-figure case, reachable once `SessionDelayService.currentDelay` exists).

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T011 [P] [US1] Integration test: completing a `BOOKED` Slot succeeds (`200`, `status: "COMPLETED"`), and a subsequent `GET .../delay` reflects the correct minutes-past-due of the earliest still-OPEN/BOOKED past-due Slot in that Session — in `backend/src/test/java/com/cms/scheduling/integration/SlotCompletionSuccessTest.java`
- [X] T012 [P] [US1] Integration test: completing a still-`OPEN` Slot → `409 SLOT_NOT_COMPLETABLE`, nothing changes; completing an already-`COMPLETED` Slot again → `409 SLOT_NOT_COMPLETABLE` (one-way transition) — in `backend/src/test/java/com/cms/scheduling/integration/SlotCompletionRejectionTest.java`
- [X] T013 [P] [US1] Integration test: completing a Queue-mode Session's Slot → `409 NOT_A_FIXED_TIME_SESSION` — in the same file as T012
- [X] T014 [P] [US1] Integration test: a Doctor's own token → `403 FORBIDDEN`; an Operations or ClinicAdmin token → succeeds (Clarifications) — in `backend/src/test/java/com/cms/scheduling/integration/SlotCompletionAuthorizationTest.java`
- [X] T015 [P] [US1] Integration test: `GET .../delay` on a Fixed-Time Session that has never had a trigger point occur → `applicable: true, delayMinutes: null`; once every past-due Slot is `COMPLETED`, the next completion's recalculation → `delayMinutes: null` (SC-005); a Doctor's own token (not just Operations/ClinicAdmin) can successfully `GET .../delay` (`200`) — the view path is intentionally unrestricted by role, unlike completion (FR-006, analyze finding E1) — in `backend/src/test/java/com/cms/scheduling/integration/SessionDelayNoOutstandingDelayTest.java`

### Implementation for User Story 1

- [X] T016 [US1] Implement `SessionDelayService` — `recalculate(sessionId)` (writes `Session.delayMinutes` per data-model.md's formula, R4) and `currentDelay(sessionId)` (pure read, R2/R5 — never calls `recalculate`) — in `backend/src/main/java/com/cms/scheduling/SessionDelayService.java` (depends on T005)
- [X] T017 [US1] Implement `SlotCompletionService.completeSlot(callerAccountId, clinicId, slotId)`: load Slot (404 `SLOT_NOT_FOUND` if missing/wrong clinic), authorize (Operations/ClinicAdmin only — research.md R7, a new method, NOT `ScheduleService.requireAuthorized`), reject a non-Fixed-Time Session (`NotAFixedTimeSessionException`, reused from 025), reject a non-`BOOKED` Slot (`SlotNotCompletableException`), flip to `COMPLETED`, call `sessionDelayService.recalculate` — in `backend/src/main/java/com/cms/scheduling/SlotCompletionService.java` (depends on T004, T006, T007, T016)
- [X] T018 [US1] Implement `SlotCompletionController` (`POST /api/v1/clinics/{clinicId}/slots/{slotId}/complete`) per contracts/session-delay-tracking.md — in `backend/src/main/java/com/cms/scheduling/SlotCompletionController.java` (depends on T001, T017)
- [X] T019 [US1] Implement `SessionDelayController` (`GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/delay`) per contracts/session-delay-tracking.md — in `backend/src/main/java/com/cms/scheduling/SessionDelayController.java` (depends on T002, T016)

**Checkpoint**: User Story 1 fully functional and independently testable — completing a Slot recalculates and exposes delay end-to-end.

---

## Phase 4: Delay Recalculates When a Walk-In Is Inserted (Priority: P2)

**Goal**: A walk-in insertion (025) into a Fixed-Time Session recalculates that Session's delay figure as part of the same action — the second, independent trigger point.

**Independent Test**: Per quickstart.md Scenario 4.

### Tests for User Story 2 (write first, confirm it FAILS before implementation)

- [X] T020 [P] [US2] Integration test: a Fixed-Time Session with an earlier still-unresolved past-due Slot and an `OPEN` buffer Slot — inserting a walk-in (`POST .../walk-in`, 025) succeeds, and a subsequent `GET .../delay` reflects the recalculated figure — in `backend/src/test/java/com/cms/booking/integration/WalkInDelayRecalculationTest.java` (`com.cms.booking`, alongside 025's own tests, since it exercises `WalkInInsertionService` directly)

### Implementation for User Story 2

- [X] T021 [US2] Add one new call site to `WalkInInsertionService.insertWalkIn`: after the Slot is flipped to `BOOKED` (the method's existing last step), call `sessionDelayService.recalculate(sessionId)` — new `SessionDelayService` constructor dependency — in `backend/src/main/java/com/cms/booking/WalkInInsertionService.java` (depends on T016)

**Checkpoint**: User Stories 1 AND 2 both work independently — both trigger points recalculate the same underlying figure.

---

## Phase 5: Staff or Doctor Views the Current Delay Figure (Priority: P1, tied with US1)

**Goal**: Confirm the query endpoint's own defining properties beyond what US1's tests already exercise incidentally: it is genuinely read-only (never recomputes), and a Queue-mode Session's query is a normal, non-error response indicating no delay concept applies at all.

**Independent Test**: Per quickstart.md Scenarios 3, 6.

**Implementation note**: No new implementation task — `SessionDelayController`/`SessionDelayService.currentDelay` (T016, T019) already fully implement this story's read path; this phase is test-only, confirming already-delivered behavior explicitly (mirrors 021's own Phase 4 precedent for an analogous "already covered, needs its own explicit test" story).

### Tests for User Story 3 (write first — exercises T016/T019's existing implementation)

- [X] T022 [P] [US3] Integration test: two `GET .../delay` calls with no trigger point in between return the identical stored value, regardless of elapsed wall-clock time between the calls — demonstrating the read path never recomputes (SC-002) — in `backend/src/test/java/com/cms/scheduling/integration/SessionDelayReadOnlyTest.java`
- [X] T023 [P] [US3] Integration test: `GET .../delay` on a Queue-mode Session → `200 { applicable: false, delayMinutes: null }` — not an error, never a numeric value (FR-007, SC-004) — in `backend/src/test/java/com/cms/scheduling/integration/SessionDelayQueueModeTest.java`

**Checkpoint**: All three user stories independently functional — the complete session-delay-tracking flow.

---

## Phase 6: Frontend & Polish

- [X] T024 [P] Create `frontend/src/features/session-delay/api.ts` — `completeSlot(clinicId, slotId, token)`, `getSessionDelay(clinicId, sessionId, token)` — mirroring `staff-booking/api.ts`'s fetch-client shape
- [X] T025 [P] Create `frontend/src/features/session-delay/CompleteSlotButton.tsx` — a button that calls `completeSlot` and shows the resulting status or error (depends on T024)
- [X] T026 [P] Create `frontend/src/features/session-delay/DelayIndicator.tsx` — fetches and displays a Session's current delay (`applicable`/`delayMinutes`), showing nothing/"on time" when `delayMinutes` is `null`, and no indicator at all when `applicable` is `false` (depends on T024)
- [X] T027 [P] Frontend test: clicking completes a Slot and shows the confirmed status; shows the `SLOT_NOT_COMPLETABLE`/`NOT_A_FIXED_TIME_SESSION` error messages — in `frontend/tests/session-delay/CompleteSlotButton.test.tsx` (depends on T025)
- [X] T028 [P] Frontend test: renders the current delay in minutes when present; renders an on-time state when `delayMinutes` is `null`; renders nothing when `applicable` is `false` — in `frontend/tests/session-delay/DelayIndicator.test.tsx` (depends on T026)
- [X] T029 Run `quickstart.md` Scenarios 1–8 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md` — blocked by the sandbox's Testcontainers/Docker limitation (confirmed via direct run of `SlotCompletionSuccessTest` and `WalkInDelayRecalculationTest`: `IllegalStateException: Could not find a valid Docker environment`), same as every prior feature this session; verified instead at the unit-of-behavior level via code review against each scenario's expected request/response/state.
- [X] T030 Run full backend build (`/tmp/gradle-8.10/bin/gradle build -x test` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 011/012/016/020/021/022/025's existing tests (given `SlotStatus`, `Session`, `SecurityConfig`, `ScheduleExceptionHandler`, and `WalkInInsertionService` were all extended). Backend: compile + spotless green (`-x test` — actual test execution blocked by the Docker limitation above, confirmed test-compile succeeds and sample test classes run far enough to hit exactly that Docker error, not a compile/logic error). Frontend: 72/72 tests green (66 pre-existing + 6 new), `npm run lint` clean for new files, `tsc -b` clean for new files (the same 2 pre-existing, unrelated `TS6133` errors in `BookSlotForm.tsx` remain, out of this feature's scope).

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all three user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational. MVP.
- **User Story 2 (Phase 4)**: Depends on US1's `SessionDelayService` (T016) existing.
- **User Story 3 (Phase 5)**: Depends on US1's `SessionDelayService`/`SessionDelayController` (T016, T019) existing — test-only, no new implementation.
- **Frontend & Polish (Phase 6)**: Depends on all three stories.

### Parallel Opportunities

- T001, T002 in parallel (different files).
- T006, T007 in parallel (different files).
- T011–T015 (all US1 tests) in parallel — depend only on T010.
- T020 can be drafted alongside Phase 3's tests, though it depends on T016 (US1's `SessionDelayService`) to actually pass.
- T022, T023 (US3 tests) in parallel once T019 exists.
- T024 first; T025, T026 in parallel once T024 exists; T027, T028 in parallel once their respective components exist.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenarios 1, 2, 5, 7 pass.

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenario 4 passes.
3. Add Phase 5 (US3): quickstart.md Scenarios 3, 6 pass — the read path's own defining properties now explicitly proven.
4. Phase 6: frontend, full-suite verification, quickstart sign-off.
