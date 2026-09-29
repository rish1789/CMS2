# Phase 0 Research: Remove Reserved-Capacity Walk-In Slots

## Decision 1: Delete `BufferSlotCalculator` itself, not just its implementations

**Decision**: Remove the `BufferSlotCalculator` interface entirely, along with both
implementations (`ColdStartBufferSlotCalculator` from 018, `RiskBasedBufferSlotCalculator` from
024), and `SlotGenerationService`'s dependency on it.

**Rationale**: The interface exists solely as a seam so 024 could swap in a data-driven
implementation without touching 018's slot-creation/distribution logic (its own javadoc says so
directly). Once there is no buffer concept left to size, the seam has zero remaining purpose —
keeping an unused interface "in case a similar need returns" is exactly the speculative-generality
this project's constitution (Principle II, Simplicity & YAGNI) rules out. `SlotGenerationService`
drops the `bufferSlotCalculator` constructor parameter and the `computeEvenlySpacedIndices` call
entirely; every generated `Slot` is created with `isBuffer` gone from its constructor.

**Alternatives considered**: Keep the interface with a no-op/always-zero implementation, in case a
future feature wants a similar seam. Rejected — YAGNI; if a future feature needs this shape again,
it can reintroduce it against its own real requirement, informed by an actual need rather than a
guess about what that need will look like.

## Decision 2: Migration is a single forward-only `DROP COLUMN`

**Decision**: `V35__drop_slot_is_buffer.sql`: `ALTER TABLE slot DROP COLUMN is_buffer;`

**Rationale**: `is_buffer` is a plain `NOT NULL BOOLEAN DEFAULT false` column (`V10__create_slot.sql`) with no dependent constraint, index, or foreign key referencing it. Dropping it is a
single, safe, non-blocking DDL statement. Per this project's Flyway convention (forward-only,
corrected by a new migration rather than editing a shipped one) and per spec.md's own Edge Cases
(already-generated reserved-capacity slots simply become ordinary slots — no backfill needed,
since the column's value was never surfaced as meaningful data outside the removed code paths).

**Alternatives considered**: Soft-deprecate the column (stop reading it, leave it in the schema).
Rejected — the spec explicitly calls for full removal, and a lingering unused column is exactly
the kind of dead weight this feature exists to eliminate.

## Decision 3: `WalkInInsertionService.selectTier` collapses from 3 tiers to 2, in place

**Decision**: Remove the buffer-slot branch (currently checked first) from `selectTier`. The
method keeps its existing shape — find a `NO_SHOW` slot first, else fall back to any `OPEN`
regular slot requiring an override reason — just with the buffer branch deleted, not
reordered or reimplemented.

**Rationale**: This is 025's own documented tier search, now missing its first tier. FR-004/FR-005
in spec.md require exactly this: no-show-freed slots keep their priority, the override-reason
requirement for a regular slot is unchanged, and nothing new is introduced to replace the removed
tier. The `!s.isBuffer()` filter on the regular-slot branch is also removed (redundant once no
slot is ever a buffer slot).

**Alternatives considered**: Re-derive a "priority" tier from some other signal (e.g. always
prefer the earliest-starting open slot) to preserve a 3-tier feel. Rejected — spec.md's User Story
2 and FR-004/005 are explicit that the fallback is exactly today's remaining two tiers, unchanged;
inventing a new tier here would be scope creep this feature isn't asking for.

## Decision 4: Backend test disposition — delete vs. update, file by file

A full-codebase sweep (not just the original scoping search) found the true, complete list. Some
tests exist solely to prove buffer-slot behavior and are deleted outright; others use a buffer
slot only incidentally as a test fixture (e.g. "create a slot that's guaranteed not to be picked
by some other code path") and are updated in place, not deleted.

**Deleted outright** (test the removed behavior directly, nothing left to assert once it's gone):

- `backend/src/test/java/com/cms/booking/integration/StaffBookingBufferSlotRejectionTest.java`
- `backend/src/test/java/com/cms/booking/integration/PatientBookingBufferSlotRejectionTest.java`
- `backend/src/test/java/com/cms/booking/integration/WalkInBufferSlotTest.java`
- `backend/src/test/java/com/cms/booking/integration/RiskBasedBufferSlotCalculatorTest.java`
- `backend/src/test/java/com/cms/booking/integration/AbstractBufferSlotCalculatorIntegrationTest.java`
  (confirmed: `RiskBasedBufferSlotCalculatorTest` is its only subclass — nothing else extends it)
- `backend/src/test/java/com/cms/scheduling/integration/SlotPreGenerationBufferPlacementTest.java`

**Updated in place** (buffer slot used only as an incidental fixture, or one test method among
several needs to go while its siblings stay):

- `backend/src/test/java/com/cms/scheduling/unit/SlotGenerationServiceTest.java` — drop the
  `bufferSlotCalculator` mock/constructor wiring; the break-window tests and the midnight-wrap
  regression test (057-adjacent work from this same session) are unaffected and stay.
- `backend/src/test/java/com/cms/booking/integration/AbstractStaffBookingIntegrationTest.java`,
  `AbstractPatientBookingIntegrationTest.java`, `AbstractWalkInIntegrationTest.java` — remove the
  `aBufferSlotOf(...)` helper method and any `isBuffer` constructor argument now that `Slot`'s
  constructor no longer takes one.
- `backend/src/test/java/com/cms/inbox/integration/AbstractInboxIntegrationTest.java`,
  `backend/src/test/java/com/cms/waitlist/integration/WaitlistOfferInboxAutoResolveTest.java` —
  same incidental-fixture cleanup.
- `backend/src/test/java/com/cms/booking/integration/WalkInNoShowFeeBlockTest.java` — drops the
  now-meaningless "book the buffer slot so it's unavailable" setup step (there is no buffer tier
  left to compete with).
- `backend/src/test/java/com/cms/booking/integration/WalkInNoShowSlotTest.java` — delete only the
  `bufferSlotIsUsedOverNoShowSlotWhenBothAreAvailable` test method (asserts exactly the removed
  priority ordering); rename
  `noShowSlotIsUsedWhenNoBufferSlotIsOpenAndOldBookingIsReplaced` to drop the now-inaccurate
  "WhenNoBufferSlotIsOpen" clause and remove its now-unnecessary buffer-slot setup, keeping the
  substance of the test (a no-show slot is reused and its old booking replaced).
- `backend/src/test/java/com/cms/booking/integration/WalkInRegularSlotOverrideTest.java` — still a
  valid test (override reason required for a regular open slot); update its comments/setup that
  currently frame this as "tier 3" now that it's tier 2.
- `backend/src/test/java/com/cms/booking/integration/WalkInQueueModeRejectionTest.java` — a single
  stale comment mentioning `isBuffer=false`; trivial wording fix, no behavior change.

## Decision 5: Frontend disposition

**Decision**: Remove `isBuffer` from the `Slot` type in `frontend/src/features/day-sheet/api.ts`,
and in `SessionSlotsView.tsx` remove the "Reserved capacity" label, the "No direct booking — use
'Insert a walk-in'" message, and the conditional that suppresses the `Book` link for a buffer
slot — every slot row now always offers `Book` when `OPEN`, exactly like any other status branch
already does. Update `SessionSlotsView.test.tsx`'s two dedicated buffer-slot test cases (`marks a
buffer slot as reserved capacity with no Book action`, `shows the real patient on a buffer slot
that has actually been booked`) — the first is deleted (asserts the removed behavior), the second
is retrospectively unnecessary as a *buffer*-specific case since an ordinary booked-slot test
already covers "a booked slot shows the real patient," but the underlying scenario (a slot fixture
using `isBuffer: true`) simply has that field dropped from its fixture like every other test
fixture in the suite.

**Rationale**: Directly satisfies FR-003 (no reserved-capacity messaging anywhere) and FR-001
(every slot directly bookable) from the user-facing side.

## Decision 6: No contract-schema versioning needed

**Decision**: `isBuffer` disappearing from `SessionDaySheetResponse` and the frontend `Slot` type
is treated as a simple field removal, not a versioned API change.

**Rationale**: This project has no API-versioning scheme for internal staff/patient endpoints (it
already lets the backend and frontend API shapes move as a matched pair per feature — the pattern
every prior feature in this backlog follows). The field's removal also removes the last thing that
ever read it (a field nobody reads is not a breaking change to any consumer of this codebase).
