---

description: "Task list for Fixed-Time Session Slot Pre-Generation"
---

# Tasks: Fixed-Time Session Slot Pre-Generation

**Input**: Design documents from `/specs/018-fixed-time-slot-pregeneration/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/slot-generation.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 slot pre-generation core, US2 = P2 buffer count/placement) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 011/013/015's existing module: `backend/src/main/java/com/cms/scheduling/`, `backend/src/test/java/com/cms/scheduling/integration/`.

---

## Phase 1: Setup

**Purpose**: New entity/enum/migration shared by both stories.

- [X] T001 [P] Create `SlotStatus` enum (`OPEN`) in `backend/src/main/java/com/cms/scheduling/SlotStatus.java`
- [X] T002 Create migration `V10__create_slot.sql` — `slot` table per data-model.md — in `backend/src/main/resources/db/migration/V10__create_slot.sql`
- [X] T003 Create `Slot` entity per data-model.md in `backend/src/main/java/com/cms/scheduling/Slot.java` (depends on T001, T002)

**Checkpoint**: Schema and types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Repository and the buffer-count seam both stories build on.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T004 [P] Create `SlotRepository` (`findBySession_Id`) in `backend/src/main/java/com/cms/scheduling/SlotRepository.java` (depends on T003)
- [X] T005 [P] Create `BufferSlotCalculator` interface and `ColdStartBufferSlotCalculator` implementation (always returns `1`) per data-model.md/research.md in `backend/src/main/java/com/cms/scheduling/BufferSlotCalculator.java` and `ColdStartBufferSlotCalculator.java`

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: Every Slot of a Fixed-Time Session Exists the Moment the Session Does (Priority: P1) 🎯 MVP

**Goal**: `SlotGenerationService.generateSlotsFor` correctly computes regularly-spaced OPEN Slots for a Fixed-Time Session's window; the hook into `SessionGenerationService.generateForSchedule` fires only for Fixed-Time, never Queue/Token, and never duplicates on repeat.

**Independent Test**: Per quickstart.md Scenarios 1, 2, 4.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T006 [P] [US1] Integration test: a Fixed-Time Session generated via `SessionGenerationService.generate()` has the correct Slot count, correct spacing, and every Slot `status = OPEN`, in `backend/src/test/java/com/cms/scheduling/integration/SlotPreGenerationFixedTimeTest.java`
- [X] T007 [P] [US1] Integration test: a Queue/Token Session generated the same way has zero Slots, in `backend/src/test/java/com/cms/scheduling/integration/SlotPreGenerationQueueModeTest.java`
- [X] T008 [P] [US1] Integration test: a window that doesn't divide evenly by the interval never produces a trailing partial Slot; calling `generate()` again produces no additional Slots for the same Session, in `backend/src/test/java/com/cms/scheduling/integration/SlotPreGenerationNoDuplicateOnRepeatTest.java`

### Implementation for User Story 1

- [X] T009 [US1] Implement `SlotGenerationService.generateSlotsFor(Session)` (regular-slot computation only for this task; buffer marking added in US2) per data-model.md in `backend/src/main/java/com/cms/scheduling/SlotGenerationService.java` (depends on T004, T005)
- [X] T010 [US1] Add the Fixed-Time hook to `SessionGenerationService.generateForSchedule` per data-model.md in `backend/src/main/java/com/cms/scheduling/SessionGenerationService.java` (depends on T009)

**Checkpoint**: User Story 1 fully functional and independently testable.

---

## Phase 4: Buffer Slots Are Reserved and Spread Evenly (Priority: P2)

**Goal**: Exactly `bufferSlotCalculator.calculateBufferSlotCount(session)` of a Session's Slots are marked `isBuffer = true`, positioned per the even-spacing formula.

**Independent Test**: Per quickstart.md Scenario 3.

### Tests for User Story 2 (write first, confirm they FAIL before implementation)

- [X] T011 [P] [US2] Integration test: a 16-Slot Fixed-Time Session has exactly 1 buffer Slot, at index 8 (the middle); a 1-Slot Session's sole Slot is itself the buffer Slot, in `backend/src/test/java/com/cms/scheduling/integration/SlotPreGenerationBufferPlacementTest.java`

### Implementation for User Story 2

- [X] T012 [US2] Add buffer-index calculation and marking to `SlotGenerationService.generateSlotsFor` per data-model.md/research.md's even-spacing formula in `backend/src/main/java/com/cms/scheduling/SlotGenerationService.java` (depends on T009)

**Checkpoint**: Both user stories independently functional — full pre-generation, correctly buffered.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T013 Run `quickstart.md` Scenarios 1–4 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T014 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected); confirm green with zero regressions in prior features' tests (including 011/015's own session-generation tests)

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational.
- **User Story 2 (Phase 4)**: Depends on US1's `generateSlotsFor` existing (extends the same method).
- **Polish (Phase 5)**: Depends on both stories.

### Parallel Opportunities

- T001 in parallel with T002/T003 (different files, though T003 depends on both).
- T004, T005 in parallel.
- T006–T008 (all US1 tests) in parallel — depend only on Foundational.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenarios 1, 2, 4 pass (no buffer marking yet — every Slot `isBuffer = false`).

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenario 3 passes.
3. Phase 5: full-suite verification and quickstart sign-off.
