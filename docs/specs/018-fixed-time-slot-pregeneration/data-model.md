# Data Model: Fixed-Time Session Slot Pre-Generation

## `SlotStatus` (new enum)

`OPEN` — this feature's only defined value (research.md).

## `Slot` (new entity)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, generated | |
| `session` | `Session`, `@ManyToOne`, required | |
| `startTime` | `LocalTime`, required | |
| `endTime` | `LocalTime`, required | `startTime + session.slotIntervalMinutes` |
| `isBuffer` | boolean | Tags one of the regularly-spaced slots as buffer capacity (FR-006/FR-007) - never an additional slot beyond the time window |
| `status` | `SlotStatus`, required | Always `OPEN` at creation (FR-002) |
| `createdAt` | `Instant`, defaulted `now()` | |

## `BufferSlotCalculator` (new interface)

```java
public interface BufferSlotCalculator {
    int calculateBufferSlotCount(Session session);
}
```

## `ColdStartBufferSlotCalculator` (new — v1's only implementation)

Always returns `1` (research.md — the literal, currently-unconditional value of 022's own "fewer than 5 data points" rule, since no no-show data exists anywhere yet).

## Service flow

### `SlotGenerationService.generateSlotsFor(Session session) -> List<Slot>`

1. Compute the regular slot start times: `session.startTime`, `+interval`, `+2*interval`, ... while `slotStart + interval <= session.endTime` (Edge Cases: no partial trailing slot).
2. `bufferCount = bufferSlotCalculator.calculateBufferSlotCount(session)`.
3. Compute the set of buffer indices among the `M` regular slots using `index(i) = floor((i + 0.5) * M / bufferCount)` for `i = 0..bufferCount-1` (research.md).
4. Build one `Slot` per computed start time, `isBuffer = true` iff its index is in the buffer-index set, `status = OPEN`, save all, return them.

### `SessionGenerationService.generateForSchedule` (extended — 011)

Immediately after `Session saved = scheduleRepository... sessionRepository.save(session)` for a **newly created** Session (never for one skipped by the existing-dates pre-check), add:

```java
if (saved.getMode() == ScheduleMode.FIXED_TIME) {
    slotGenerationService.generateSlotsFor(saved);
}
```

Inside the same `@Transactional generateForSchedule` call — no separate transaction, no separate pass (FR-001, research.md).

## Request/Response contract

See `contracts/slot-generation.md` — no HTTP endpoint (spec Assumptions); the contract is `SlotGenerationService`/`BufferSlotCalculator`'s Java interface shape.
