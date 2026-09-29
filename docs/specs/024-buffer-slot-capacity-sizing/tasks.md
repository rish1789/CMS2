---

description: "Task list for Buffer Slot Capacity Sizing"
---

# Tasks: Buffer Slot Capacity Sizing

**Input**: Design documents from `/specs/024-buffer-slot-capacity-sizing/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 flat-default floor, US2 = P2 risk-based computation) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 015/016/018/023's existing modules: `backend/src/main/java/com/cms/booking/`, `backend/src/test/java/com/cms/booking/integration/`. No frontend (spec Assumptions - no HTTP endpoint or UI).

---

## Phase 1: Setup

- [X] T001 Add `findByDoctorAndFixedTimeWindow(doctorProfileId, windowStart, windowEnd)` query to `BookingRepository` per data-model.md/research.md in `backend/src/main/java/com/cms/booking/BookingRepository.java`

**Checkpoint**: Query exists; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T002 Create `AbstractBufferSlotCalculatorIntegrationTest.java` — Testcontainers fixture with Clinic/DoctorProfile helpers and a helper to directly construct a Fixed-Time `Session` + `Booking` at an explicit `LocalDate` (for controllable trailing-window placement) and status (`BOOKED` or `NO_SHOW`) — in `backend/src/test/java/com/cms/booking/integration/AbstractBufferSlotCalculatorIntegrationTest.java` (depends on T001)

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: A New/Low-History Doctor Gets the Flat Default Buffer (Priority: P1) 🎯 MVP

**Goal**: `RiskBasedBufferSlotCalculator` correctly falls back to `ColdStartBufferSlotCalculator`'s existing `1` below the 5-sample threshold, and existing 018 tests (which create no booking history) still pass unchanged.

**Independent Test**: Per quickstart.md Scenario 1.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T003 [P] [US1] Integration test: a doctor with 0 Bookings in the trailing window gets a buffer count of 1, in `backend/src/test/java/com/cms/booking/integration/RiskBasedBufferSlotCalculatorTest.java`
- [X] T004 [P] [US1] Integration test (same file): a doctor with exactly 4 Bookings (any status) in the trailing window still gets 1 (below the 5-sample floor); a doctor with 5+ Bookings all dated more than 90 days before the session date still gets 1 (proves the window boundary actually excludes them, not just that a low total count falls back — FR-005)

### Implementation for User Story 1

- [X] T005 [US1] Implement `RiskBasedBufferSlotCalculator` — `@Component @Primary`, implements `com.cms.scheduling.BufferSlotCalculator`, injects `ColdStartBufferSlotCalculator` (by concrete type) and `BookingRepository`; below-5-samples branch delegates to `ColdStartBufferSlotCalculator` per research.md — in `backend/src/main/java/com/cms/booking/RiskBasedBufferSlotCalculator.java` (depends on T001, T002)

**Checkpoint**: User Story 1 fully functional; run the existing `SlotPreGenerationBufferPlacementTest` suite to confirm zero regression (it creates no booking history, so both its assertions — 1 buffer slot — remain correct under the new implementation).

---

## Phase 4: A Doctor With Sufficient History Gets a Risk-Based Buffer Count (Priority: P2)

**Goal**: The full risk-based formula — direct-cap percentage, 20% cap, 3-slot absolute cap, zero-rate case, even distribution unaffected.

**Independent Test**: Per quickstart.md Scenarios 2–5.

### Tests for User Story 2 (write first, confirm they FAIL before implementation)

- [X] T006 [P] [US2] Integration test (same file as T003): a doctor with 5+ Bookings and a nonzero no-show rate gets a buffer count computed from that rate (direct-cap percentage, Clarifications)
- [X] T007 [P] [US2] Integration test (same file): a doctor with 5+ Bookings, none `NO_SHOW`, gets a computed buffer count of 0 (FR-008 — the flat default does not apply above the sample threshold)
- [X] T008 [P] [US2] Integration test (same file): a doctor with a very high no-show rate and a large session never exceeds 20% of the session's slots as buffer
- [X] T009 [P] [US2] Integration test (same file): a doctor whose 20%-of-slots figure alone would exceed 3 is capped at 3
- [X] T010 [P] [US2] Integration test (same file): whatever buffer count is computed, generating the session's actual Slots still distributes them evenly (018's existing, unchanged `computeEvenlySpacedIndices` — a regression check, not new behavior)

### Implementation for User Story 2

- [X] T011 [US2] Extend `RiskBasedBufferSlotCalculator`'s 5+-samples branch with the full formula — no-show rate, direct-cap at 20%, session total-slot recomputation from `startTime`/`endTime`/`slotIntervalMinutes`, round, cap at 3 — per data-model.md (depends on T005)

**Checkpoint**: Both user stories independently functional — the complete buffer-sizing formula.

---

## Phase 5: Polish

- [X] T012 Run `quickstart.md` Scenarios 1–5 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T013 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected); confirm green with zero regressions in 018's existing `SlotPreGenerationBufferPlacementTest`/`SlotPreGenerationFixedTimeTest` suites (the `BufferSlotCalculator` bean Spring now injects into `SlotGenerationService` has changed)

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational.
- **User Story 2 (Phase 4)**: Depends on US1's T005 existing (extends the same class).
- **Polish (Phase 5)**: Depends on both stories.

### Parallel Opportunities

- T003, T004 in parallel; T006–T010 (all same file) drafted together — all depend only on T002.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenario 1 passes; 018's existing buffer-placement tests still pass unchanged.

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenarios 2–5 pass.
3. Phase 5: full-suite verification, quickstart sign-off.
