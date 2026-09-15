# Data Model: Session Delay Tracking (Fixed-Time Only)

## Slot (extended)

| Field | Type | Change | Notes |
|---|---|---|---|
| `status` | `SlotStatus` | **extended enum** | Gains `COMPLETED`, a fourth value reachable only from `BOOKED` (via `SlotCompletionService`). |

No new column — `SlotStatus` is persisted as a string (`@Enumerated(EnumType.STRING)`), so a new
enum constant needs no migration, exactly as `BOOKED` (016) and `NO_SHOW` (021) required none.

## Session (extended)

| Field | Type | Change | Notes |
|---|---|---|---|
| `delayMinutes` | `Integer`, nullable | **new** | `null` = no outstanding delay (R2 — collapses "never triggered" and "computed to zero/negative" into one representation). Written only by `SessionDelayService.recalculate(...)`; never computed on read. |

**Migration**: `V15__session_delay_minutes.sql` —
```sql
ALTER TABLE session ADD COLUMN delay_minutes INTEGER;
```
Nullable, no default, no backfill (every existing Session row correctly starts with no outstanding
delay computed yet — the same "no trigger has occurred" state R2 already treats as `null`).

## State Transitions

```
Slot.status:
  BOOKED --[SlotCompletionService.completeSlot, Fixed-Time only]--> COMPLETED
```

No other transition touches `COMPLETED` — it is a terminal state (Assumptions: one-way, no
un-completing), and only a `BOOKED` Slot may enter it (`OPEN` and already-`COMPLETED` are both
rejected — FR-001).

```
Session.delayMinutes:
  recalculated (overwritten, never incrementally adjusted) by SessionDelayService.recalculate(...),
  called from exactly two sites:
    1. SlotCompletionService.completeSlot(...) — after the Slot flips to COMPLETED
    2. WalkInInsertionService.insertWalkIn(...) [025, extended in place] — after a successful insertion
```

## Validation Rules (from Functional Requirements)

- FR-001: `completeSlot` MUST reject a Slot whose `status != BOOKED` (covers both "still OPEN,
  nothing to complete" and "already COMPLETED, one-way transition").
- FR-002 / FR-003: both trigger call sites MUST call `recalculate` as part of the same transaction
  as the state change that triggered them — never deferred, never batched.
- FR-004: `recalculate` MUST select the *earliest* (by `startTime`) OPEN/BOOKED Slot whose
  `sessionDate` + `startTime` has already passed as of "now" at the trigger point; if none, store
  `null`.
- FR-005: `SessionDelayService.currentDelay(sessionId)` (the read path) MUST NOT call
  `recalculate` — it only returns the stored `Session.delayMinutes`.
- FR-006 / FR-007: the read path MUST return a response distinguishable between "Fixed-Time, X
  minutes or none" and "Queue-mode, not applicable" (R6's `applicable` flag) — never a bare
  number/null alone.
- FR-008: `completeSlot` MUST reject a Slot belonging to a Queue-mode Session
  (`NotAFixedTimeSessionException`, reused from 025).
- FR-009: `completeSlot` MUST be rejected for any caller who isn't an active Operations staff
  member or ClinicAdmin at the Slot's clinic (Clarifications) — reuses 016/020/025's authorization
  shape exactly, not `ScheduleService`'s doctor-inclusive one (research.md R7).
