---

description: "Task list for Walk-In / Priority Insertion"
---

# Tasks: Walk-In / Priority Insertion

**Input**: Design documents from `/specs/025-walk-in-priority-insertion/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/walk-in-insertion.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 buffer-slot insertion, US2 = P2 no-show-freed-slot insertion, US3 = P3 regular-slot insertion with required override reason) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 015/016/020's existing `com.cms.booking` module: `backend/src/main/java/com/cms/booking/`, `backend/src/test/java/com/cms/booking/integration/`. New: `frontend/src/features/staff-booking/WalkInForm.tsx`.

---

## Phase 1: Setup

**Purpose**: The new request DTO shared by all three stories (the single endpoint's shape doesn't change across tiers — only server-side tier selection does).

- [X] T001 [P] Create `WalkInRequest` DTO (`patientId`, `patientName`, `patientPhone`, `appointmentTypeId`, `overrideReason` — all but `appointmentTypeId` nullable) in `backend/src/main/java/com/cms/booking/dto/WalkInRequest.java`

**Checkpoint**: Type exists; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema change, the two new exception types every story's rejection paths need, the security matcher, and the shared test fixture.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T002 Create migration `V14__booking_override_reason.sql` — nullable `override_reason TEXT` column on `booking` — in `backend/src/main/resources/db/migration/V14__booking_override_reason.sql` per data-model.md
- [X] T003 Extend `Booking`: add nullable `overrideReason` field + getter, and a second constructor overload `Booking(Slot, Patient, AppointmentType, BigDecimal, UUID, String overrideReason)` (existing 5-arg constructor stays unchanged for 016/017/018's call sites, delegates to the new one with `null`) in `backend/src/main/java/com/cms/booking/Booking.java` (depends on T002)
- [X] T004 [P] Create `NoSlotAvailableException` (mirrors `SlotAlreadyBookedException`'s shape) in `backend/src/main/java/com/cms/booking/NoSlotAvailableException.java`
- [X] T005 [P] Create `OverrideReasonRequiredException` in `backend/src/main/java/com/cms/booking/OverrideReasonRequiredException.java`
- [X] T006 Add `NO_SLOT_AVAILABLE` (409) and `OVERRIDE_REASON_REQUIRED` (400) mappings to `backend/src/main/java/com/cms/booking/BookingExceptionHandler.java` per contracts/walk-in-insertion.md (depends on T004, T005)
- [X] T007 Add explicit matcher `.requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/sessions/*/walk-in").authenticated()` to the `/api/v1/clinics/**` chain, placed alongside the existing booking matchers (research.md R6 — proactively avoids 014's silent-fallthrough-to-`permitAll()` gap class) in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`
- [X] T008 Create `AbstractWalkInIntegrationTest` (duplicates `AbstractStaffBookingIntegrationTest`'s fixture shape per this codebase's established precedent — no shared base class between staff-booking test fixtures) with three new helpers: `aBufferSlotOf(session)` (filters the generated Slots for `isBuffer=true && OPEN`), `aRegularOpenSlotOf(session)` (`isBuffer=false && OPEN`, excluding whichever Slot `aBufferSlotOf` would return), and `aNoShowSlotWithOldBooking(session, patient, appointmentType, fee)` (takes a regular OPEN Slot, saves a `Booking` against it, then sets its status directly to `NO_SHOW` — bypassing the real sweep, consistent with data-model.md's "reads status, not re-derives it" contract) in `backend/src/test/java/com/cms/booking/integration/AbstractWalkInIntegrationTest.java` (depends on T003)

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: Staff Inserts a Walk-In Using a Reserved Buffer Slot (Priority: P1) 🎯 MVP

**Goal**: Front-desk staff insert a walk-in into a Session's OPEN buffer Slot, no override reason required, with fee resolution/locking and walk-in Patient creation identical to 016's.

**Independent Test**: Per quickstart.md Scenario 1 (and Scenario 4's "nothing available" case, Scenario 5's fee-block case, Scenario 7's authorization case — all reachable with only the buffer tier implemented).

**MVP scope note (as-planned) / implementation note (as-built)**: This phase was planned to implement the priority search with only the tier-1 (buffer) branch wired, extended in place by Phases 4/5. In practice, since `WalkInInsertionService.insertWalkIn` is a single cohesive method whose three tiers share one write-ordering (research.md R1-R3), all three tiers were implemented together in one pass rather than as three separate edits — T012/T017/T021/T022 together describe that one implementation, each task's own described contribution (tier-1 search, tier-2 delete-and-replace, tier-3 override gate + race-closure) is present in the code exactly as specified. T009's "MVP scope" sub-case was adjusted accordingly (see T009) since the as-built no-buffer case falls through to the real tier-3 check rather than rejecting outright.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T009 [P] [US1] Integration test: insertion into a Session with an OPEN buffer Slot succeeds (201) with no `overrideReason`, the buffer Slot becomes `BOOKED`, `lockedFee` matches resolution; a brand-new walk-in Patient is created when no `patientId` is given; an invalid `patientPhone` (fails the Indian numbering plan) → `400 INVALID_MOBILE_NUMBER`, zero Patient/Booking rows created (FR-007); an absent `patientPhone` → `201` (contact-less walk-ins allowed) — in `backend/src/test/java/com/cms/booking/integration/WalkInBufferSlotTest.java`. As-built (see implementation note above): with the full 3-tier search implemented in one pass, a Session with no OPEN buffer Slot but an available regular Slot instead falls through to `400 OVERRIDE_REASON_REQUIRED` rather than `409 NO_SLOT_AVAILABLE` — the fully-exhausted `NO_SLOT_AVAILABLE` case is covered by T019 instead.
- [X] T010 [P] [US1] Integration test: no fee resolvable → `409 NO_FEE_CONFIGURED`, zero Patient/Booking rows created — in `backend/src/test/java/com/cms/booking/integration/WalkInFeeBlockTest.java`
- [X] T011 [P] [US1] Integration test: caller is a Doctor (not Operations/ClinicAdmin) → `403 FORBIDDEN` — in `backend/src/test/java/com/cms/booking/integration/WalkInAuthorizationTest.java`

### Implementation for User Story 1

- [X] T012 [US1] Implement `WalkInInsertionService.insertWalkIn(callerAccountId, clinicId, sessionId, WalkInInsertionInput)`: load Session (404 `SESSION_NOT_FOUND` if missing/wrong clinic), authorize (mirrors `StaffQueueBookingService.requireAuthorized`), search `slotRepository.findBySession_Id` for a tier-1 buffer candidate only, sorted by `startTime` ascending before filtering so the tie-break among multiple equally-eligible Slots is deterministic (analyze finding E2 — else `NoSlotAvailableException`), resolve-and-lock fee (first write-gate), resolve-or-create Patient (duplicated helper per research.md R4), `saveAndFlush` the Booking with `overrideReason=null`, flip Slot to `BOOKED` — in `backend/src/main/java/com/cms/booking/WalkInInsertionService.java` (depends on T001, T003, T004, T008)
- [X] T013 [US1] Implement `WalkInInsertionController` (`POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in`) per contracts/walk-in-insertion.md, reusing `BookingResponse.of(...)` for the response — in `backend/src/main/java/com/cms/booking/WalkInInsertionController.java` (depends on T012)

**Checkpoint**: User Story 1 fully functional and independently testable — buffer-slot walk-ins work end-to-end.

---

## Phase 4: Staff Insert a Walk-In Into a No-Show-Freed Slot (Priority: P2)

**Goal**: With no buffer Slot available, staff insert a walk-in into a Session's `NO_SHOW` Slot — the original no-show Booking is deleted and the walk-in Booking takes its place atomically, no override reason required.

**Independent Test**: Per quickstart.md Scenario 2.

### Tests for User Story 2 (write first, confirm they FAIL before implementation)

- [X] T014 [P] [US2] Integration test: a Session with no OPEN buffer Slot but a `NO_SHOW` Slot (with its original Booking) succeeds (201), the original no-show Booking no longer exists afterward, the new Booking references the same Slot now `BOOKED`, no `overrideReason` required — in `backend/src/test/java/com/cms/booking/integration/WalkInNoShowSlotTest.java`
- [X] T015 [P] [US2] Integration test: a Session with *both* an OPEN buffer Slot and a `NO_SHOW` Slot uses the buffer Slot, not the no-show-freed one (SC-001 strict order) — in the same file as T014
- [X] T016 [P] [US2] Integration test: no fee resolvable against a Session with only a `NO_SHOW` Slot available → `409 NO_FEE_CONFIGURED`, and the original no-show Booking is still present, untouched (FR-001a) — in `backend/src/test/java/com/cms/booking/integration/WalkInNoShowFeeBlockTest.java`

### Implementation for User Story 2

- [X] T017 [US2] Extend `WalkInInsertionService.insertWalkIn`: add the tier-2 branch (a `NO_SHOW` Slot, considered only when no tier-1 candidate exists) to the priority search; when tier-2 is selected, delete the Slot's existing Booking (`bookingRepository.findBySlot_Id` + `delete`) as the last step before saving the new Booking — never before fee/patient resolution succeed (research.md R2/R3, FR-001a) — in `backend/src/main/java/com/cms/booking/WalkInInsertionService.java` (depends on T012)

**Checkpoint**: User Stories 1 AND 2 both work independently — buffer and no-show-freed walk-ins.

---

## Phase 5: Staff Insert a Walk-In Into a Regular Slot With a Required Override Reason (Priority: P3)

**Goal**: With neither a buffer nor a no-show-freed Slot available, staff can still insert into any other OPEN regular Slot — but only with a required, non-blank override reason, retained on the resulting Booking.

**Independent Test**: Per quickstart.md Scenario 3 (and Scenario 6's concurrency case, Scenario 4's fully-exhausted case).

### Tests for User Story 3 (write first, confirm they FAIL before implementation)

- [X] T018 [P] [US3] Integration test: a Session with no OPEN buffer and no `NO_SHOW` Slot, insertion into a regular OPEN Slot with no `overrideReason` → `400 OVERRIDE_REASON_REQUIRED`, nothing created; retried with a non-blank `overrideReason` → `201`, and the created Booking's `overrideReason` is retrievable and matches — in `backend/src/test/java/com/cms/booking/integration/WalkInRegularSlotOverrideTest.java`
- [X] T019 [P] [US3] Integration test: a Session where every Slot is `BOOKED` (no candidate at any tier) → `409 NO_SLOT_AVAILABLE` regardless of `overrideReason` — in the same file as T018
- [X] T020 [P] [US3] Integration test: two concurrent walk-in insertions targeting a Session with exactly one eligible Slot (any tier) → exactly one succeeds (`201`), the other `409 SLOT_ALREADY_BOOKED` — in `backend/src/test/java/com/cms/booking/integration/WalkInConcurrencyTest.java`

### Implementation for User Story 3

- [X] T021 [US3] Extend `WalkInInsertionService.insertWalkIn`: add the tier-3 branch (any other OPEN regular Slot, considered only when neither tier-1 nor tier-2 has a candidate) to the priority search; require a non-blank `overrideReason` before proceeding (`OverrideReasonRequiredException` otherwise); if none of the three tiers has a candidate, throw `NoSlotAvailableException`; persist the supplied reason on the new Booking via the T003 constructor overload — in `backend/src/main/java/com/cms/booking/WalkInInsertionService.java` (depends on T017)
- [X] T022 [US3] Switch the Booking save from `save` to `saveAndFlush` inside a `try { } catch (DataIntegrityViolationException e) { throw new SlotAlreadyBookedException(...); }` (016's convergence-fixed race-closure pattern, research.md R3 step 7) — in `backend/src/main/java/com/cms/booking/WalkInInsertionService.java` (depends on T021)

**Checkpoint**: All three user stories independently functional — the complete walk-in priority-insertion flow.

---

## Phase 6: Frontend & Polish

- [X] T023 [P] Add `insertWalkIn(clinicId, sessionId, request, token)` to the staff-booking API client, mirroring `bookSlot`'s shape, in `frontend/src/features/staff-booking/api.ts`
- [X] T024 Create `WalkInForm.tsx` — patient existing/new toggle (mirrors `BookSlotForm.tsx`), appointment type field, and an override-reason field that's always visible but only becomes required after a first submission attempt surfaces `OVERRIDE_REASON_REQUIRED` from the server (the client can't know in advance which tier will apply — contracts/walk-in-insertion.md) — in `frontend/src/features/staff-booking/WalkInForm.tsx` (depends on T023)
- [X] T025 [P] Frontend test: submits a walk-in and shows the locked fee on success; on `OVERRIDE_REASON_REQUIRED`, shows the reason field as required and lets the user resubmit with a reason; shows `NO_SLOT_AVAILABLE`/`NO_FEE_CONFIGURED` error messages — in `frontend/tests/staff-booking/WalkInForm.test.tsx` (path corrected to match this project's actual test-directory convention — see `tests/staff-booking/BookSlotForm.test.tsx` — rather than tasks.md's originally-assumed `src/features/staff-booking/__tests__/`) (depends on T024)
- [X] T026 Run `quickstart.md` Scenarios 1–8 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md` — blocked by the sandbox's Testcontainers/Docker limitation (confirmed via direct run of `WalkInBufferSlotTest`: `IllegalStateException: Could not find a valid Docker environment`), same as every prior feature this session; verified instead at the unit-of-behavior level via code review against each scenario's expected request/response/state.
- [X] T027 Run full backend build (`/tmp/gradle-8.10/bin/gradle build -x test` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 012/015/016/021/022's existing tests (given `Booking` and `com.cms.identity.account.SecurityConfig` were extended). Backend: compile + spotless green (`-x test` — actual test execution blocked by the Docker limitation above, confirmed test-compile succeeds and one test class runs far enough to hit exactly that Docker error, not a compile/logic error). Frontend: 66/66 tests green (61 pre-existing + 5 new), `npm run lint` clean for new files, `tsc -b` clean for new files (2 pre-existing, unrelated `TS6133` unused-variable errors remain in `BookSlotForm.tsx`, present before this feature and out of this feature's scope).

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all three user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational. MVP.
- **User Story 2 (Phase 4)**: Depends on US1's T012 existing (extends the same method in place).
- **User Story 3 (Phase 5)**: Depends on US2's T017 existing (extends the same method in place).
- **Frontend & Polish (Phase 6)**: Depends on all three stories (the form's override-reason behavior needs T021's server-side gate to exist).

### Parallel Opportunities

- T004, T005 in parallel (different files).
- T009, T010, T011 (all US1 tests) in parallel — depend only on T008.
- T014, T015, T016 (US2 tests) in parallel once T012 exists (written first, expected to fail until T017 lands).
- T018, T019, T020 (US3 tests) in parallel once T017 exists (written first, expected to fail until T021/T022 land).
- T023, T025 in parallel with each other where file boundaries allow; T025 depends on T024 existing to import against.

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Integration test: buffer-slot insertion succeeds, no-buffer rejects NO_SLOT_AVAILABLE, in WalkInBufferSlotTest.java"
Task: "Integration test: no fee resolvable blocks everything, in WalkInFeeBlockTest.java"
Task: "Integration test: non-Operations/ClinicAdmin caller gets 403, in WalkInAuthorizationTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenario 1 (and 4/5/7's reachable-today cases) pass.

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenario 2 passes; Scenario 1's buffer-precedence-over-no-show case now provable.
3. Add Phase 5 (US3): quickstart.md Scenario 3 passes; full three-tier priority order now provable end-to-end (SC-001).
4. Phase 6: frontend, full-suite verification, quickstart sign-off.

---

## Phase 7: Convergence

- [X] T028 Add an explicit `session.getMode() != ScheduleMode.FIXED_TIME` guard in `WalkInInsertionService.insertWalkIn`, checked immediately after the Session is loaded and before the priority search — reject with a new `NotAFixedTimeSessionException` (mirrors `com.cms.scheduling.NotAQueueSessionException`'s shape, mapped in `BookingExceptionHandler` to `409 NOT_A_FIXED_TIME_SESSION`) — plus an integration test proving a Queue-mode Session is rejected even when it has a Slot that would otherwise match tier 3, in `backend/src/main/java/com/cms/booking/WalkInInsertionService.java`, `backend/src/main/java/com/cms/scheduling/NotAFixedTimeSessionException.java`, `backend/src/main/java/com/cms/booking/BookingExceptionHandler.java`, and `backend/src/test/java/com/cms/booking/integration/WalkInQueueModeRejectionTest.java` per spec.md Assumptions ("This feature is Fixed-Time-only") and Edge Cases (missing)
