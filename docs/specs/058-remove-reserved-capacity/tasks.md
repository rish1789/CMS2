---

description: "Task list for Remove Reserved-Capacity Walk-In Slots"
---

# Tasks: Remove Reserved-Capacity Walk-In Slots

**Input**: Design documents from `/specs/058-remove-reserved-capacity/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/reserved-capacity-removal.md, quickstart.md

**Tests**: Included per this project's constitution (Principle I, Test-First Development, NON-NEGOTIABLE). Integration tests (Testcontainers) are updated/deleted and compiled but unexecuted, per this project's standing sandbox Docker limitation (every prior feature in this backlog carries the same note).

**Organization**: This is a coordinated removal, not additive work — the domain field (`Slot.isBuffer`) and every consumer are compile-coupled, so the actual code removal happens as one block in Phase 2 (Foundational). Phases 3-5 map to spec.md's three user stories through the test/verification work that proves each story's specific acceptance criteria, per research.md Decision 4's full file-by-file classification.

## Phase 1: Setup

No setup tasks. This feature adds zero new dependencies (plan.md Technical Context) and removes code from two existing modules (`scheduling`, `booking`) plus the frontend's existing Day Sheet feature.

---

## Phase 2: Foundational (Coordinated Removal — Blocking Prerequisite for All Stories)

**Purpose**: The compile-atomic removal of `Slot.isBuffer` and every production-code consumer. This MUST complete, as one block, before any story's test/verification work can be meaningful — the domain field and its consumers cannot be removed independently without breaking compilation partway through (research.md Decision 1).

- [x] T001 Remove the `isBuffer` field, both getter/constructor signatures in `backend/src/main/java/com/cms/scheduling/domain/Slot.java` (data-model.md): drop `is_buffer` column mapping, `isBuffer` field, `isBuffer()` getter, and the `boolean isBuffer` parameter from the Fixed-Time constructor. Update the Queue/Token constructor's javadoc to drop its "isBuffer stays false" clause.
- [x] T002 [P] Add `backend/src/main/resources/db/migration/V35__drop_slot_is_buffer.sql`: `ALTER TABLE slot DROP COLUMN is_buffer;` (research.md Decision 2 — single forward-only DDL, no dependent constraint/index).
- [x] T003 Remove the `BufferSlotCalculator` dependency, `computeEvenlySpacedIndices` method, and buffer-index wiring from `backend/src/main/java/com/cms/scheduling/service/SlotGenerationService.java`: drop the constructor parameter, the `bufferCount`/`bufferIndices` locals, and the now-4th-arg-less `new Slot(session, start, end)` call. Depends on T001.
- [x] T004 [P] Delete `backend/src/main/java/com/cms/scheduling/service/BufferSlotCalculator.java` (the seam interface — zero callers remain once T003 lands). Depends on T003.
- [x] T005 [P] Delete `backend/src/main/java/com/cms/scheduling/service/ColdStartBufferSlotCalculator.java` (018's flat-default implementation). Depends on T003.
- [x] T006 [P] Delete `backend/src/main/java/com/cms/booking/service/RiskBasedBufferSlotCalculator.java` (024's risk-scoring implementation). Depends on T003.
- [x] T007 Remove `AND s.isBuffer = false` from both the `value` and `countQuery` of `findOpenFixedTimeSlots` and `findOpenFixedTimeSlotsOnDate` in `backend/src/main/java/com/cms/scheduling/repository/SlotRepository.java` (contracts/reserved-capacity-removal.md — open-slot listings now include every `OPEN` slot). Depends on T001.
- [x] T008 Remove the `slot.isBuffer()` guard (and its `BufferSlotNotDirectlyBookableException` throw) from `backend/src/main/java/com/cms/booking/service/PatientBookingService.java`'s `bookSlot`. Depends on T001.
- [x] T009 Remove the `slot.isBuffer()` guard (and its `BufferSlotNotDirectlyBookableException` throw) from `backend/src/main/java/com/cms/booking/service/StaffBookingService.java`'s `bookSlot`. Depends on T001.
- [x] T010 [P] Delete `backend/src/main/java/com/cms/booking/exception/BufferSlotNotDirectlyBookableException.java` (zero throw sites remain). Depends on T008, T009.
- [x] T011 Remove the `@ExceptionHandler(BufferSlotNotDirectlyBookableException.class)` mapping (`handleBufferSlotNotDirectlyBookable`, the `SLOT_RESERVED_FOR_WALK_IN` response) from `backend/src/main/java/com/cms/booking/exception/BookingExceptionHandler.java`. Depends on T010.
- [x] T012 Remove the buffer-slot tier (`Slot::isBuffer` first-priority branch) and the `!s.isBuffer()` filter from the regular-slot branch in `backend/src/main/java/com/cms/booking/service/WalkInInsertionService.java`'s `selectTier` (data-model.md — 3-tier search becomes 2-tier: no-show-freed, then regular-with-override). Depends on T001.
- [x] T013 Remove the `isBuffer` field and its `slot.isBuffer()` mapping from `backend/src/main/java/com/cms/booking/dto/SessionDaySheetResponse.java`. Depends on T001.
- [x] T014 Remove `isBuffer: boolean` from the `Slot` type in `frontend/src/features/day-sheet/api.ts`. Depends on T013.
- [x] T015 Remove the "Reserved capacity" label, the "No direct booking — use 'Insert a walk-in'" message, and the conditional suppressing the `Book` link, from `frontend/src/features/day-sheet/SessionSlotsView.tsx` — every `OPEN` slot row now always offers `Book`. Depends on T014.

**Checkpoint**: `isBuffer` no longer exists anywhere in the codebase — domain, schema, generation, repository, booking guards, walk-in tier search, DTO, and frontend are all consistent. `./gradlew compileJava compileTestJava` and `npx tsc -b` are expected to still fail at this point until Phase 3-5's test-file updates land (the deleted/updated test files below still reference removed symbols) — that's the next phase's job, not a Foundational defect.

---

## Phase 3: User Story 1 - Every generated slot is directly bookable (Priority: P1) 🎯 MVP

**Goal**: Confirm no slot is ever rejected for being reserved capacity, anywhere a slot can be booked or browsed, and no Day Sheet row shows reserved-capacity messaging.

**Independent Test**: Generate a new day's Fixed-Time sessions and confirm every resulting slot can be booked directly by a patient or by staff, with none rejected for being reserved capacity (spec.md US1).

### Tests for User Story 1

- [x] T016 [P] [US1] Delete `backend/src/test/java/com/cms/booking/integration/StaffBookingBufferSlotRejectionTest.java` (tests the removed `StaffBookingService` guard — nothing left to assert).
- [x] T017 [P] [US1] Delete `backend/src/test/java/com/cms/booking/integration/PatientBookingBufferSlotRejectionTest.java` (tests the removed `PatientBookingService` guard).
- [x] T018 [P] [US1] Update `frontend/tests/day-sheet/SessionSlotsView.test.tsx`: delete the `marks a buffer slot as reserved capacity with no Book action` case (asserts the removed UI); drop `isBuffer` from every remaining slot fixture in the file (matches the type change in T014).

### Implementation for User Story 1

- [x] T019 [US1] Live-verify via `quickstart.md` Scenario 1 against the running dev servers: generate sessions, confirm no "Reserved capacity"/"No direct booking" text anywhere on the Day Sheet, confirm staff can book any slot directly, confirm a patient's open-slot browsing list matches the Day Sheet's `Open` rows exactly. Depends on Phase 2 and T016-T018.

**Checkpoint**: User Story 1 independently verified — every slot is directly bookable, with automated coverage and a live pass confirming it end-to-end.

---

## Phase 4: User Story 2 - Walk-ins still get placed sensibly, without a reserved tier (Priority: P2)

**Goal**: Confirm walk-in insertion's 2-tier fallback (no-show-freed, then regular-with-override) behaves exactly as spec.md's FR-004/FR-005 require, with the removed buffer tier leaving no trace.

**Independent Test**: With no reserved slots available (since none exist anymore), insert a walk-in and confirm it's offered a no-show-freed slot when one exists, and otherwise a regular open slot with an explicit override reason (spec.md US2).

### Tests for User Story 2

- [x] T020 [P] [US2] Delete `backend/src/test/java/com/cms/booking/integration/WalkInBufferSlotTest.java` (tests the removed buffer-tier priority — nothing left to assert).
- [x] T021 [P] [US2] Delete `backend/src/test/java/com/cms/booking/integration/AbstractBufferSlotCalculatorIntegrationTest.java` and `backend/src/test/java/com/cms/booking/integration/RiskBasedBufferSlotCalculatorTest.java` together (confirmed: the latter is the former's only subclass — research.md Decision 4).
- [x] T022 [US2] In `backend/src/test/java/com/cms/booking/integration/WalkInNoShowSlotTest.java`: delete the `bufferSlotIsUsedOverNoShowSlotWhenBothAreAvailable` test method (asserts exactly the removed priority ordering); rename `noShowSlotIsUsedWhenNoBufferSlotIsOpenAndOldBookingIsReplaced` to `noShowSlotIsUsedAndOldBookingIsReplaced` and remove its now-unnecessary buffer-slot setup, keeping the substance (a no-show slot is reused, its old booking replaced).
- [x] T023 [P] [US2] Update `backend/src/test/java/com/cms/booking/integration/WalkInNoShowFeeBlockTest.java`: remove the now-meaningless "book the buffer slot so it's unavailable" setup step — there is no buffer tier left to compete with.
- [x] T024 [P] [US2] Update `backend/src/test/java/com/cms/booking/integration/WalkInRegularSlotOverrideTest.java`: update comments/framing that currently describe this as "tier 3" now that it's tier 2 — the override-reason-required behavior itself is unchanged.
- [x] T025 [P] [US2] Update `backend/src/test/java/com/cms/booking/integration/WalkInQueueModeRejectionTest.java`: fix the stale comment mentioning `isBuffer=false` (wording only, no behavior change).
- [x] T026 [P] [US2] Remove the `aBufferSlotOf(...)` helper method (and any `isBuffer` constructor argument) from `backend/src/test/java/com/cms/booking/integration/AbstractStaffBookingIntegrationTest.java`, `AbstractPatientBookingIntegrationTest.java`, and `AbstractWalkInIntegrationTest.java` — matches `Slot`'s simplified constructor from T001.
- [x] T027 [P] [US2] Remove incidental `isBuffer` fixture usage from `backend/src/test/java/com/cms/inbox/integration/AbstractInboxIntegrationTest.java` and `backend/src/test/java/com/cms/waitlist/integration/WaitlistOfferInboxAutoResolveTest.java`.

### Implementation for User Story 2

- [x] T028 [US2] Live-verify via `quickstart.md` Scenario 2 against the running dev servers: a no-show-freed slot is still preferred first with no override reason required; with no no-show slot, a regular open slot requires an override reason; with zero open slots, the existing "no slot available" rejection is unchanged. Depends on Phase 2 and T020-T027.

**Checkpoint**: User Story 2 independently verified — walk-in insertion's 2-tier fallback works exactly as spec'd, with automated coverage and a live pass.

---

## Phase 5: User Story 3 - No-show handling no longer factors into slot generation sizing (Priority: P3)

**Goal**: Confirm a doctor's no-show history produces zero measurable difference in slot generation, since the mechanism that read it (the risk-based calculator) no longer exists.

**Independent Test**: Generate sessions for a doctor with a high recent no-show rate and confirm slot generation produces the same count and spacing of slots as for any other doctor (spec.md US3).

### Tests for User Story 3

- [x] T029 [P] [US3] Delete `backend/src/test/java/com/cms/scheduling/integration/SlotPreGenerationBufferPlacementTest.java` (tests buffer placement/even-spacing — nothing left to assert).
- [x] T030 [US3] Update `backend/src/test/java/com/cms/scheduling/unit/SlotGenerationServiceTest.java`: remove the `bufferSlotCalculator` mock and its constructor wiring in `newService()`/`generate()`. The break-window tests and the midnight-wrap regression test (from this session's earlier F1 fix) are unaffected and stay unchanged.

### Implementation for User Story 3

- [x] T031 [US3] Live-verify via `quickstart.md` Scenario 3 against the running dev servers: compare generated slot count/spacing for a doctor with a high recent no-show rate against one with none, same schedule shape — confirm they're identical. Depends on Phase 2 and T029-T030.

**Checkpoint**: All three user stories independently verified — the full removal as specified.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T032 [P] `cd backend && ./gradlew spotlessApply test` — full backend unit + contract suite green (integration tests compiled, unexecuted per this sandbox's standing Docker limitation, consistent with every prior feature in this backlog).
- [x] T033 [P] `cd frontend && npx tsc -b` — zero type errors.
- [x] T034 [P] `cd frontend && npm run lint` — zero new warnings in any file this feature touches.
- [x] T035 `cd frontend && npm run test -- --run` — full suite green, zero regressions (aside from the pre-existing, unrelated `PatientHubPage.test.tsx` hardcoded-date flake already tracked separately).
- [x] T036 Grep the full repo for `isBuffer`, `BufferSlotCalculator`, `ColdStartBufferSlotCalculator`, `RiskBasedBufferSlotCalculator`, `BufferSlotNotDirectlyBookable`, and `SLOT_RESERVED_FOR_WALK_IN` — confirm zero remaining references anywhere in `backend/` or `frontend/` (source and tests), per FR-001 through FR-007's "MUST NOT" requirements.

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: T001-T015, mostly sequential (each consumer's removal depends on T001 having already dropped the field/method it references) with a few `[P]` deletions once their sole caller is gone. Blocks every user story — nothing in Phase 3-5 compiles until this phase is complete.
- **US1 (T016-T019)**: depends on Foundational. Test deletions (T016-T018) are `[P]` (different files, no interdependency); the live-verify (T019) depends on all of them plus Foundational.
- **US2 (T020-T028)**: depends on Foundational. Most test updates are `[P]` (different files); T022 is its own task since it's a single-file, two-part edit. The live-verify (T028) depends on all of them plus Foundational.
- **US3 (T029-T031)**: depends on Foundational. T029 (`[P]`) and T030 touch different files. The live-verify (T031) depends on both plus Foundational.
- **Polish (Phase 6)**: depends on all three user stories being complete. T036 is the final, repo-wide confirmation that removal is total.

## Parallel Example: Foundational Deletions

```bash
# Once T003 lands (SlotGenerationService no longer references these), these three
# deletions touch different files and can run in parallel:
Task: "Delete BufferSlotCalculator.java"
Task: "Delete ColdStartBufferSlotCalculator.java"
Task: "Delete RiskBasedBufferSlotCalculator.java"
```

## Parallel Example: User Story 2 Test Updates

```bash
# T023-T027 touch different test files - parallelizable:
Task: "Update WalkInNoShowFeeBlockTest.java - remove buffer setup step"
Task: "Update WalkInRegularSlotOverrideTest.java - tier comment fix"
Task: "Update WalkInQueueModeRejectionTest.java - stale comment fix"
Task: "Remove aBufferSlotOf helper from the three Abstract*IntegrationTest base classes"
Task: "Remove incidental isBuffer fixture usage from inbox/waitlist integration tests"
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 2: Foundational (the coordinated removal — this is most of the actual work).
2. Complete Phase 3: User Story 1 — proves the core "every slot is directly bookable" outcome.
3. **STOP and VALIDATE**: run `quickstart.md` Scenario 1.

### Incremental Delivery

1. Foundational → US1 (MVP) → validate → demo.
2. US2 (walk-in 2-tier fallback verification) → validate → demo.
3. US3 (no-show-rate-independence verification) → validate → demo.
4. Polish once all three are in.

## Notes

- Total: 36 tasks (15 Foundational + 4 US1 + 9 US2 + 3 US3 + 5 Polish).
- This is a removal, not additive work — Foundational necessarily carries the bulk of the actual
  code change, since `Slot.isBuffer` and its consumers are compile-coupled (research.md Decision 1
  explains why the interface itself, not just its implementations, is deleted). The three user-
  story phases carry the test disposition and live-verification work that proves each of spec.md's
  three independently-stated outcomes, even though the underlying implementation landed as one
  block.
- No new migration content beyond the single `DROP COLUMN` (research.md Decision 2) — no backfill,
  no data transformation, per spec.md's own Edge Cases.
- `WalkInInsertionService`'s `Tier` record shape is unchanged (data-model.md) — only the branch
  that used to produce a buffer-tier `Tier` is removed, so no downstream code needs to change.
