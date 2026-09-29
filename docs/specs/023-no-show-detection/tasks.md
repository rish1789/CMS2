---

description: "Task list for Automatic No-Show Detection"
---

# Tasks: Automatic No-Show Detection

**Input**: Design documents from `/specs/023-no-show-detection/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 detection itself, US2 = P2 no-waitlist-side-effect confirmation) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 012/015/018/020's existing `com.cms.scheduling` module: `backend/src/main/java/com/cms/scheduling/`, `backend/src/test/java/com/cms/scheduling/integration/`. No frontend (spec Assumptions - no HTTP endpoint or UI).

---

## Phase 1: Setup

- [X] T001 [P] Create migration `V13__slot_no_show_and_hold_support.sql` — `ALTER TABLE slot ADD COLUMN on_hold BOOLEAN NOT NULL DEFAULT false` — in `backend/src/main/resources/db/migration/V13__slot_no_show_and_hold_support.sql`
- [X] T002 [P] Add `NO_SHOW` to `SlotStatus` in `backend/src/main/java/com/cms/scheduling/SlotStatus.java`

**Checkpoint**: Schema and status value exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T003 Add `onHold` (boolean, default `false`, getter + setter) to `Slot` in `backend/src/main/java/com/cms/scheduling/Slot.java` per data-model.md (depends on T001)
- [X] T004 Add `findBookedFixedTimeCandidatesForNoShow()` query to `SlotRepository` per data-model.md in `backend/src/main/java/com/cms/scheduling/SlotRepository.java` (depends on T002, T003)
- [X] T005 Create `NoShowDetectionTest`'s base fixture — extends `AbstractSlotGenerationIntegrationTest` (reusing its Clinic/DoctorProfile helpers and Slot cleanup), adding a helper that directly constructs a `Session` + `BOOKED` `Slot` at an explicit `LocalTime` relative to `LocalTime.now()` (not via `sessionGenerationService`'s fixed daily window, which can't reliably be "in the past" relative to whenever the test suite happens to run) — in `backend/src/test/java/com/cms/scheduling/integration/AbstractNoShowDetectionIntegrationTest.java` (depends on T004)

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: System Auto-Marks an Unattended Booking as No-Show (Priority: P1) 🎯 MVP

**Goal**: `NoShowDetectionService.detectAndMarkNoShows()` correctly identifies and flips only the right Slots, exactly once, excluding held and Queue-mode Slots.

**Independent Test**: Per quickstart.md Scenarios 1–5.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T006 [P] [US1] Integration test: a `BOOKED` Fixed-Time Slot scheduled more than 10 minutes in the past is marked `NO_SHOW` in `backend/src/test/java/com/cms/scheduling/integration/NoShowDetectionTest.java`
- [X] T007 [P] [US1] Integration test (same file): a `BOOKED` Fixed-Time Slot scheduled less than 10 minutes in the past remains `BOOKED`
- [X] T008 [P] [US1] Integration test (same file): a `BOOKED` Fixed-Time Slot on hold (`onHold = true`), well past its grace period, remains `BOOKED`
- [X] T009 [P] [US1] Integration test (same file): running the sweep twice against an already-`NO_SHOW` Slot, and once against an `OPEN` Slot with no Booking, changes nothing either time
- [X] T010 [P] [US1] Integration test (same file): a `BOOKED` Queue/Token Slot, well past its issuance time, is untouched (Clarifications - Fixed-Time only)

### Implementation for User Story 1

- [X] T011 [US1] Implement `NoShowDetectionService.detectAndMarkNoShows()` — queries candidates via T004, filters by elapsed grace period in Java, updates each via a direct `slotRepository.save(slot)` call with **no self-invoked `@Transactional` helper** (research.md — the exact bug class found twice already this session), returns the count marked — in `backend/src/main/java/com/cms/scheduling/NoShowDetectionService.java` (depends on T005)
- [X] T012 [US1] Implement `NoShowDetectionTrigger` — `@Component`, `@Scheduled(cron = "0 * * * * *")`, mirrors `NightlySessionGenerationTrigger`'s exact shape — in `backend/src/main/java/com/cms/scheduling/NoShowDetectionTrigger.java` (depends on T011)

**Checkpoint**: User Story 1 fully functional and independently testable.

---

## Phase 4: No-Show Never Triggers a Waitlist Bump (Priority: P2)

**Goal**: Confirm the sweep's only side effect is the Slot's own status flip — nothing else is touched.

**Independent Test**: Per quickstart.md Scenario 1, extended with a Booking-unchanged assertion.

**Implementation note**: No new implementation task — `NoShowDetectionService` (T011) never
references `Booking` or any waitlist-related code at all (none exists yet in this backlog), so
this guarantee holds structurally. This phase's test exists to make that guarantee explicit and
regression-proof, not to add new behavior.

### Tests for User Story 2

- [X] T013 [P] [US2] Integration test: after a Slot is marked `NO_SHOW`, its associated `Booking` row (lockedFee, paymentStatus, patient) is completely unchanged — in `backend/src/test/java/com/cms/scheduling/integration/NoShowDetectionTest.java` (depends on T011)

**Checkpoint**: Both user stories independently functional — the complete no-show detection flow.

---

## Phase 5: Polish

- [X] T014 Run `quickstart.md` Scenarios 1–5 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T015 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected); confirm green with zero regressions in 012/015/016/018/020's existing tests (given `SlotStatus` and `Slot` were extended)

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS the user story's test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational.
- **User Story 2 (Phase 4)**: Depends on US1's T011 existing (its test exercises the same implementation, confirming a property of it).
- **Polish (Phase 5)**: Depends on both stories.

### Parallel Opportunities

- T001, T002 in parallel (different files).
- T006–T010 (all US1 tests, same file) drafted together — depend only on T005.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenarios 1–5 pass.

### Incremental Delivery

2. Add Phase 4 (US2): the Booking-unchanged guarantee is explicitly tested.
3. Phase 5: full-suite verification, quickstart sign-off.
