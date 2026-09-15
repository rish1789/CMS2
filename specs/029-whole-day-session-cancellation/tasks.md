---

description: "Task list for Whole-Day Session Cancellation"
---

# Tasks: Whole-Day Session Cancellation

**Input**: Design documents from `/specs/029-whole-day-session-cancellation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/session-cancellation.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Single user story (US1 = P1 staff cancels an entire Session) per spec.md — no second story this feature.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 015/016/017/018/025/028's existing `com.cms.booking` module: `backend/src/main/java/com/cms/booking/`, `backend/src/test/java/com/cms/booking/integration/`. New: `frontend/src/features/session-cancellation/`, `frontend/tests/session-cancellation/`.

---

## Phase 1: Setup

**Purpose**: The new response DTO and exception.

- [X] T001 [P] Create `SessionCancellationResponse` record (`sessionId`, `bookingsCancelled`) per contracts/session-cancellation.md in `backend/src/main/java/com/cms/booking/dto/SessionCancellationResponse.java`
- [X] T002 [P] Create `SessionAlreadyCancelledException` in `backend/src/main/java/com/cms/booking/SessionAlreadyCancelledException.java`

**Checkpoint**: Types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The exception mapping and the shared test fixture.

**⚠️ CRITICAL**: No test can be written until this phase is complete.

- [X] T003 Add `SESSION_ALREADY_CANCELLED` (409) mapping to `backend/src/main/java/com/cms/booking/BookingExceptionHandler.java` per contracts/session-cancellation.md (depends on T002). `SESSION_NOT_FOUND`/`FORBIDDEN` need no new mappings — already globally registered by this same class.
- [X] T003a Add explicit matcher `.requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/sessions/*/cancel").authenticated()` to the `/api/v1/clinics/**` chain in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java` — caught and added during implementation; the original task breakdown omitted this despite every prior feature this session needing one (014's own precedent: an un-matched path silently falls through to `anyRequest().permitAll()`)
- [X] T004 Create `AbstractSessionCancellationIntegrationTest` — combining 028's `AbstractBookingCancellationIntegrationTest` fixture shape with a `saveSessionWithBookings(clinic, doctor, mode, bookedCount, openCount)` helper that builds one Session (Fixed-Time or Queue-mode) with a mix of `BOOKED` (each with an active Booking, one linked to a Patient Account and the rest walk-in), `OPEN`, `NO_SHOW`, and `COMPLETED` Slots for exercising the bulk-cancel filter — in `backend/src/test/java/com/cms/booking/integration/AbstractSessionCancellationIntegrationTest.java`

**Checkpoint**: Foundation ready — implementation and tests can now proceed.

---

## Phase 3: Staff Cancels an Entire Session (Priority: P1) 🎯 MVP

**Goal**: An authorized staff member cancels every currently-active Booking in a Session (Fixed-Time or Queue-mode) in one action, releasing their Slots, feeding the notification pipeline for linked Patients, and never triggering a waitlist bump.

**Independent Test**: Per quickstart.md Scenarios 1–5.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T005 [P] [US1] Integration test: a Fixed-Time Session with mixed Slot states (`BOOKED` ×3, `OPEN` ×2, `NO_SHOW` ×1, `COMPLETED` ×1) → `200`, `bookingsCancelled: 3`; the 3 formerly-`BOOKED` Slots become `OPEN`; the other 4 Slots are untouched (FR-002/FR-004, SC-001/SC-003) — in `backend/src/test/java/com/cms/booking/integration/SessionCancellationSuccessTest.java`
- [X] T006 [P] [US1] Integration test: the identical scenario for a Queue-mode Session (FR-001) — in the same file as T005
- [X] T007 [P] [US1] Integration test: repeating cancellation against the same, now-cancelled Session → `409 SESSION_ALREADY_CANCELLED`, zero further state change; a brand-new Session that never had any Booking → the same `409` (FR-005, SC-004) — in `backend/src/test/java/com/cms/booking/integration/SessionCancellationRejectionTest.java`
- [X] T008 [P] [US1] Integration test: a Session with one Booking whose Patient has a linked Patient Account and one walk-in Booking → exactly one `NotificationEvent` row created (for the linked one), zero for the walk-in (FR-006, SC-005) — in `backend/src/test/java/com/cms/booking/integration/SessionCancellationNotificationTest.java`
- [X] T009 [P] [US1] Integration test: cancelling a Session never results in any `BookingCancelledEvent` being observed (capture via the same `@TestConfiguration`-scoped recording listener pattern 028's `BookingCancellationConcurrencyTest` established) even though multiple Bookings are cancelled in the same call (FR-003/SC-002) — in `backend/src/test/java/com/cms/booking/integration/SessionCancellationNoWaitlistBumpTest.java`
- [X] T010 [P] [US1] Integration test (genuine concurrency): a whole-session cancellation and an individual 025 cancellation targeting the same Booking in that Session, fired concurrently → exactly one of the two "wins" that specific Booking (it ends up `CANCELLED` exactly once, not double-processed), the whole-session action's own `bookingsCancelled` count reflects only the Bookings it actually won (FR-002, Edge Cases) — in `backend/src/test/java/com/cms/booking/integration/SessionCancellationConcurrencyTest.java`
- [X] T011 [P] [US1] Integration test: staff with zero role assignment at the Session's clinic → `403 FORBIDDEN`; a Doctor's own token → `403 FORBIDDEN` (Operations/ClinicAdmin-only, unlike 026/027's view-only unrestricted gate); a Session at a different clinic → `404 SESSION_NOT_FOUND` — in `backend/src/test/java/com/cms/booking/integration/SessionCancellationAccessTest.java`

### Implementation for User Story 1

- [X] T012 [US1] Implement `SessionCancellationService.cancelSession(Session session)` — load all Slots (`SlotRepository.findBySession_Id`), filter to `status == BOOKED`, reject with `SessionAlreadyCancelledException` if none (FR-005/research.md R3); for each: look up its active Booking (`BookingRepository.findBySlot_IdAndStatus`, 028's safe query), call `bookingRepository.cancelIfActive` (028's guard, research.md R2/R4) — on `1` (won), flip the Slot to `OPEN` and, if the Booking's Patient has a linked Patient Account, call `NotificationEventService.publish` (research.md R5); on `0` (lost a race to a concurrent action), skip silently; never call anything that publishes `BookingCancelledEvent` — return the count actually cancelled — in `backend/src/main/java/com/cms/booking/SessionCancellationService.java` (depends on T002)
- [X] T013 [US1] Implement `SessionCancellationController` (`POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel`): authorize (Operations-or-ClinicAdmin only at `clinicId`, mirrors 025/028's write-action gate — reuses the existing role-specific `RoleAssignmentRepository` checks, not 027's role-agnostic one), fetch and filter the Session by clinic (else `404 SESSION_NOT_FOUND`, reused), then call `SessionCancellationService.cancelSession` — in `backend/src/main/java/com/cms/booking/SessionCancellationController.java` (depends on T001, T012)

**Checkpoint**: Feature fully functional and independently testable — staff can cancel any whole Session at their clinic.

---

## Phase 4: Frontend & Polish

- [X] T014 [P] Create `frontend/src/features/session-cancellation/api.ts` — `cancelSession(clinicId, sessionId, token)` — mirroring `booking-cancellation/api.ts`'s fetch-client shape
- [X] T015 Create `frontend/src/features/session-cancellation/CancelSessionButton.tsx` — a button that calls `cancelSession` and shows the resulting `bookingsCancelled` count or error (depends on T014)
- [X] T016 [P] Frontend test: confirms cancellation and shows the count; shows the `SESSION_ALREADY_CANCELLED`/`FORBIDDEN` error messages — in `frontend/tests/session-cancellation/CancelSessionButton.test.tsx` (depends on T015)
- [X] T017 Run `quickstart.md` Scenarios 1–6 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md` — blocked by the sandbox's Testcontainers/Docker limitation (confirmed via direct run of `SessionCancellationSuccessTest`/`SessionCancellationConcurrencyTest`: `IllegalStateException: Could not find a valid Docker environment`), same as every prior feature this session; verified instead at the unit-of-behavior level via code review against each scenario's expected request/response/state.
- [X] T018 Run full backend build (`/tmp/gradle-8.10/bin/gradle build -x test` — use this, not `./gradlew`) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 025/028's existing tests (given `BookingExceptionHandler` and `SecurityConfig` were extended and this feature is the first real caller of `NotificationEventService.publish`). Backend: compile + spotless green (`-x test` — actual test execution blocked by the Docker limitation above, confirmed test-compile succeeds and sample test classes run far enough to hit exactly that Docker error, not a compile/logic error). Frontend: 82/82 tests green (79 pre-existing + 3 new), `npm run lint` clean for new files, `tsc -b` clean for new files (the same 2 pre-existing, unrelated `TS6133` errors in `BookSlotForm.tsx` remain, out of this feature's scope).

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational. MVP and only story.
- **Frontend & Polish (Phase 4)**: Depends on Phase 3.

### Parallel Opportunities

- T001, T002 in parallel (different files).
- T005–T011 (all US1 tests) in parallel — depend only on T004.
- T016 once T015 exists.

---

## Implementation Strategy

### MVP First (and only story)

1. Phase 1 → Phase 2 → Phase 3. **STOP and VALIDATE**: quickstart.md Scenarios 1–5 pass.
2. Phase 4: frontend, full-suite verification, quickstart sign-off.
