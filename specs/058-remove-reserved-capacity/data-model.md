# Phase 1 Data Model: Remove Reserved-Capacity Walk-In Slots

No new entity or table. One existing entity loses one field and its backing column; one existing
service's internal decision logic loses one branch. This document describes what disappears, not
what's added.

## `Slot` (existing entity, `com.cms.scheduling.domain.Slot`)

### Field removed: `isBuffer`

| Before | After |
|---|---|
| `boolean isBuffer` field, backed by `is_buffer BOOLEAN NOT NULL DEFAULT false` column, set at construction time by `SlotGenerationService` per a `BufferSlotCalculator`-computed count and even-spacing placement | Field and column removed entirely. A `Slot` has no concept of reserved capacity — its only distinguishing runtime state remains its `status` (Open, Booked, Appeared, No-Show, Completed) |

Both `Slot` constructors lose the `isBuffer` parameter:

- Fixed-Time constructor: `Slot(Session, LocalTime start, LocalTime end, boolean isBuffer)` →
  `Slot(Session, LocalTime start, LocalTime end)`
- Queue/Token constructor: unaffected in signature (it never took `isBuffer`), but its javadoc's
  "stays null/null/false" description drops the `isBuffer` clause since there's nothing to default.

### No new validation rules

Nothing replaces the removed `isBuffer = false` filters in `SlotRepository`'s open-slot queries —
they're deleted, not replaced with an equivalent guard, because every `OPEN` slot is now
uniformly eligible for direct booking by construction (no slot is ever created any other way).

## `WalkInInsertionService`'s tier search (existing internal logic, not a persisted entity)

### Before: 3-tier priority search

1. An `OPEN` slot where `isBuffer = true` (no override reason required)
2. A slot with status `NO_SHOW` (no override reason required; replaces the slot's prior booking)
3. Any other `OPEN` regular (`isBuffer = false`) slot (override reason **required**)

### After: 2-tier priority search

1. A slot with status `NO_SHOW` (no override reason required; replaces the slot's prior booking)
2. Any other `OPEN` slot (override reason **required**)

The internal `Tier` record's shape (`slot`, `requiresOverrideReason`, `isNoShowReplacement`) is
unchanged — only the branch that used to produce a `Tier` with both flags `false` (the buffer
case) is removed. Tier-internal tie-breaking (earliest `startTime` first) is unchanged for the two
remaining tiers.

## `SessionDaySheetResponse` (existing DTO, `com.cms.booking.dto`)

### Field removed: `isBuffer`

The per-slot entry in this DTO drops its `boolean isBuffer` field and the `slot.isBuffer()`
mapping that populated it. Every other field (`slotId`, `startTime`, `endTime`, `tokenNumber`,
`status`, `booking`) is unaffected.

## Frontend `Slot` type (existing type, `frontend/src/features/day-sheet/api.ts`)

Mirrors the DTO change: `isBuffer: boolean` is removed from the type, and every place that branched
on it (`SessionSlotsView.tsx`'s "Reserved capacity" rendering) is removed along with it — see
research.md Decision 5.
