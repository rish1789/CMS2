---

description: "Task list for Queue/Token Session Slot-on-Booking Generation"
---

# Tasks: Queue/Token Session Slot-on-Booking Generation

**Input**: Design documents from `/specs/019-queue-slot-on-demand-generation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/queue-slot-issuance.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Single user story (US1 = P1, the feature's entire scope) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 011/012's existing module: `backend/src/main/java/com/cms/scheduling/`, `backend/src/test/java/com/cms/scheduling/integration/`.

---

## Phase 1: Setup

**Purpose**: Schema/entity extension and new exceptions shared by the story.

- [X] T001 [P] Create `SessionNotFoundException`, `NotAQueueSessionException` in `backend/src/main/java/com/cms/scheduling/`
- [X] T002 Create migration `V11__slot_queue_token_support.sql` — `slot.start_time`/`end_time` become nullable, add `slot.token_number`, partial unique index `(session_id, token_number) WHERE token_number IS NOT NULL` — in `backend/src/main/resources/db/migration/V11__slot_queue_token_support.sql`
- [X] T003 Extend `Slot` — make `startTime`/`endTime` fields nullable, add `tokenNumber` field + getter, add the `Slot(Session, int)` queue-mode constructor per data-model.md — in `backend/src/main/java/com/cms/scheduling/Slot.java` (depends on T002)

**Checkpoint**: Schema and entity ready; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Repository query shared by the story's tests.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T004 Add `findMaxTokenNumberBySession_Id(UUID sessionId)` to `SlotRepository` in `backend/src/main/java/com/cms/scheduling/SlotRepository.java` (depends on T003)

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: Each Booking Issues Exactly One New Slot With the Next Unused Token (Priority: P1) 🎯 MVP

**Goal**: `QueueSlotService.issueNextSlot` correctly, sequentially, and race-safely issues never-reused token numbers, and correctly rejects wrong-mode/unknown Sessions.

**Independent Test**: Per quickstart.md Scenarios 1–4.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T005 [P] [US1] Integration test: sequential calls issue tokens 1, 2, 3, ... in order; a token already issued is never reissued (simulated "cancellation" is just skipping ahead in the sequence, per spec Edge Cases) — in `backend/src/test/java/com/cms/scheduling/integration/QueueSlotIssuanceOrderTest.java`
- [X] T006 [P] [US1] Integration test: 20 concurrent `issueNextSlot` calls against the same Session all succeed with the exact token set `{1..20}`, no duplicates, no gaps — in `backend/src/test/java/com/cms/scheduling/integration/QueueSlotIssuanceConcurrencyTest.java`
- [X] T007 [P] [US1] Integration test: `issueNextSlot` against a Fixed-Time Session throws `NotAQueueSessionException` and creates no Slot; against an unknown session id throws `SessionNotFoundException` — in `backend/src/test/java/com/cms/scheduling/integration/QueueSlotIssuanceRejectionTest.java`

### Implementation for User Story 1

- [X] T008 [US1] Implement `QueueSlotService.issueNextSlot`/`attemptIssueSlot` per data-model.md/research.md (outer non-transactional retry loop, inner `@Transactional` fresh-attempt method) in `backend/src/main/java/com/cms/scheduling/QueueSlotService.java` (depends on T001, T004)

**Checkpoint**: User Story 1 (the whole feature) fully functional and independently testable.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [X] T009 Run `quickstart.md` Scenarios 1–4 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T010 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected); confirm green with zero regressions in prior features' tests (including 012's own Slot pre-generation tests, given `Slot` was extended)

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS the user story's test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational.
- **Polish (Phase 4)**: Depends on User Story 1.

### Parallel Opportunities

- T001 in parallel with T002 (different files); T003 depends on T002.
- T005–T007 (all US1 tests) in parallel — depend only on T004.

---

## Implementation Strategy

### MVP First (and Only) Story

1. Phase 1 → Phase 2 → Phase 3. **STOP and VALIDATE**: quickstart.md Scenarios 1–4 pass.
2. Phase 4: full-suite verification and quickstart sign-off. This is the feature's entire scope.
