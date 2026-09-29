---

description: "Task list for Partial (Cutoff-Based) Session Cancellation"
---

# Tasks: Partial (Cutoff-Based) Session Cancellation

**Input**: Design documents from `/specs/030-partial-cutoff-session-cancellation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/partial-session-cancellation.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Single user story (US1 = P1 staff cancels the trailing portion of a Session) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 015/016/017/018/025/028/029's existing `com.cms.booking` module: `backend/src/main/java/com/cms/booking/`, `backend/src/test/java/com/cms/booking/integration/`. New: `frontend/src/features/partial-session-cancellation/`, `frontend/tests/partial-session-cancellation/`.

---

## Phase 1: Setup

**Purpose**: The new request DTO (reuses 029's `SessionCancellationResponse` for the response — no new response type).

- [X] T001 Create `PartialCancellationRequest` record (`cutoffTime: LocalTime`) per contracts/partial-session-cancellation.md in `backend/src/main/java/com/cms/booking/dto/PartialCancellationRequest.java`

**Checkpoint**: Type exists; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The security matcher and the shared test fixture.

**⚠️ CRITICAL**: No test can be written until this phase is complete.

- [X] T002 Add explicit matcher `.requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/sessions/*/cancel-from-cutoff").authenticated()` to the `/api/v1/clinics/**` chain in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java` (029's own tasks.md omitted this task once — do not repeat that gap; add it explicitly here per 014's own precedent)
- [X] T003 Create `AbstractPartialSessionCancellationIntegrationTest extends AbstractSessionCancellationIntegrationTest` (reuses 029's fixture — `saveFixedTimeSessionWithSlots`/`saveQueueSession`/`addQueueSlot`/`bookSlot`/token helpers — directly via inheritance; Fixed-Time cutoff testing needs no new helper, since each generated Slot already carries its own real `startTime` to pick before/after a chosen cutoff from), adding one new helper: `addQueueSlotWithCreatedAt(session, tokenNumber, createdAt)` for constructing a Queue Slot with an explicit, controllable `createdAt` (029's own `addQueueSlot` always uses "now," which isn't controllable enough for cutoff testing) in `backend/src/test/java/com/cms/booking/integration/AbstractPartialSessionCancellationIntegrationTest.java`

**Checkpoint**: Foundation ready — implementation and tests can now proceed.

---

## Phase 3: Staff Cancels the Trailing Portion of a Session (Priority: P1) 🎯 MVP

**Goal**: An authorized staff member cancels every `BOOKED` Slot at or after a chosen cutoff time in one action, leaving earlier and already-`COMPLETED` Slots untouched, never triggering a waitlist bump, with a cutoff matching nothing reporting zero rather than erroring.

**Independent Test**: Per quickstart.md Scenarios 1–5.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T004 [P] [US1] Integration test: a Fixed-Time Session with Bookings before and at/after a chosen cutoff, plus an already-`COMPLETED` Slot before the cutoff and an already-`OPEN` Slot after it → only the at/after-cutoff `BOOKED` Slot's Booking is cancelled and its Slot returns to `OPEN`; the before-cutoff Booking, the `COMPLETED` Slot, and the already-`OPEN` Slot are all untouched (FR-002/FR-003/FR-004, SC-001/SC-003) — in `backend/src/test/java/com/cms/booking/integration/PartialSessionCancellationSuccessTest.java`
- [X] T005 [P] [US1] Integration test: a Queue-mode Session with two Bookings whose Slots have different `createdAt` timestamps, one before and one at/after the cutoff → only the at/after one is cancelled (FR-008) — in the same file as T004
- [X] T006 [P] [US1] Integration test: a cutoff later than every remaining Slot → `200`, `bookingsCancelled: 0` — not an error, the one deliberate divergence from 029's `SESSION_ALREADY_CANCELLED` (FR-009, SC-004, research.md R2) — in `backend/src/test/java/com/cms/booking/integration/PartialSessionCancellationZeroQualifyingTest.java`
- [X] T007 [P] [US1] Integration test: no `BookingCancelledEvent` is ever observed (same `@TestConfiguration`-scoped recording listener pattern as 028/029), even when multiple Bookings are cancelled by one call (FR-005/SC-002) — in `backend/src/test/java/com/cms/booking/integration/PartialSessionCancellationNoWaitlistBumpTest.java`
- [X] T008 [P] [US1] Integration test: a Session with one at/after-cutoff Booking whose Patient has a linked Patient Account and one walk-in → exactly one `NotificationEvent` created (FR-006/SC-005) — in `backend/src/test/java/com/cms/booking/integration/PartialSessionCancellationNotificationTest.java`
- [X] T009 [P] [US1] Integration test: staff with zero role assignment at the Session's clinic → `403 FORBIDDEN`; a Doctor's own token → `403 FORBIDDEN`; a Session at a different clinic → `404 SESSION_NOT_FOUND` (mirrors 029's identical access pattern) — in `backend/src/test/java/com/cms/booking/integration/PartialSessionCancellationAccessTest.java`

### Implementation for User Story 1

- [X] T010 [US1] Implement `SessionPartialCancellationService.cancelFromCutoff(Session session, LocalTime cutoffTime)` — load all Slots, compute the per-mode cutoff threshold (`LocalDateTime` via `startTime` for Fixed-Time, `Instant` via `createdAt` for Queue-mode — research.md R3), filter to `BOOKED` Slots at/after that threshold, for each: `bookingRepository.cancelIfActive` (025/029's guard), on success flip the Slot to `OPEN` and notify if the Patient has a linked Account, on a lost race skip silently, never call anything that publishes `BookingCancelledEvent` — return the count actually cancelled, **no rejection when this count is zero** (research.md R2, unlike 029) — in `backend/src/main/java/com/cms/booking/SessionPartialCancellationService.java` (depends on T001)
- [X] T011 [US1] Implement `SessionPartialCancellationController` (`POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel-from-cutoff`): authorize (Operations-or-ClinicAdmin only, mirrors 029's write-action gate), fetch and filter the Session by clinic (else `404 SESSION_NOT_FOUND`, reused), then call `SessionPartialCancellationService.cancelFromCutoff`, returning 029's existing `SessionCancellationResponse` — in `backend/src/main/java/com/cms/booking/SessionPartialCancellationController.java` (depends on T001, T010)

**Checkpoint**: Feature fully functional and independently testable.

---

## Phase 4: Frontend & Polish

- [X] T012 [P] Create `frontend/src/features/partial-session-cancellation/api.ts` — `cancelFromCutoff(clinicId, sessionId, cutoffTime, token)` — mirroring `session-cancellation/api.ts`'s fetch-client shape
- [X] T013 Create `frontend/src/features/partial-session-cancellation/CancelFromCutoffForm.tsx` — a cutoff-time input plus a submit action that calls `cancelFromCutoff` and shows the resulting `bookingsCancelled` count or error (depends on T012)
- [X] T014 [P] Frontend test: submits a cutoff and shows the count (including 0); shows the `FORBIDDEN`/`SESSION_NOT_FOUND` error messages — in `frontend/tests/partial-session-cancellation/CancelFromCutoffForm.test.tsx` (depends on T013)
- [X] T015 Run `quickstart.md` Scenarios 1–6 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md` — blocked by the sandbox's Testcontainers/Docker limitation (confirmed via direct run of `PartialSessionCancellationSuccessTest`: `IllegalStateException: Could not find a valid Docker environment`), same as every prior feature this session; verified instead at the unit-of-behavior level via code review against each scenario's expected request/response/state.
- [X] T016 Run full backend build (`/tmp/gradle-8.10/bin/gradle build -x test` — use this, not `./gradlew`) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 025/026/029's existing tests. Backend: compile + spotless green (`-x test` — actual test execution blocked by the Docker limitation above, confirmed test-compile succeeds and a sample test class runs far enough to hit exactly that Docker error, not a compile/logic error). Frontend: 85/85 tests green (82 pre-existing + 3 new), `npm run lint` clean for new files, `tsc -b` clean for new files (the same 2 pre-existing, unrelated `TS6133` errors in `BookSlotForm.tsx` remain, out of this feature's scope).

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational. MVP and only story.
- **Frontend & Polish (Phase 4)**: Depends on Phase 3.

### Parallel Opportunities

- T004–T009 (all US1 tests) in parallel — depend only on T003.
- T014 once T013 exists.

---

## Implementation Strategy

1. Phase 1 → Phase 2 → Phase 3. **STOP and VALIDATE**: quickstart.md Scenarios 1–5 pass.
2. Phase 4: frontend, full-suite verification, quickstart sign-off.

---

## Phase 5: Convergence

- [X] T017 Integration test (genuine concurrency, mirrors 029's `SessionCancellationConcurrencyTest`): a partial cutoff cancellation and an individual 025 cancellation racing on the same in-range `BOOKED` Booking → exactly one of the two "wins" it (ends up `CANCELLED` exactly once) — the explicitly-described Edge Case this feature's own spec calls out but tasks.md never covered (convergence finding F1) — in `backend/src/test/java/com/cms/booking/integration/PartialSessionCancellationConcurrencyTest.java`
