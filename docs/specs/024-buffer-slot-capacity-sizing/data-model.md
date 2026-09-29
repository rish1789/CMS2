# Data Model: Buffer Slot Capacity Sizing

No new entities, no new migration. Pure new behavior (one class, one repository query) over
entities already defined by 015/016/018/023.

## Reused Entities (unchanged)

| Entity | Defined by | Fields relevant to this feature |
|---|---|---|
| `Session` | 015 (`com.cms.scheduling`) | `sessionDate`, `doctorProfile`, `mode`, `startTime`/`endTime`/`slotIntervalMinutes` (for independently recomputing total slot count — research.md). |
| `Slot` | 012/023 (`com.cms.scheduling`) | `status` (`NO_SHOW` is the signal this feature consumes). |
| `Booking` | 016 (`com.cms.booking`) | `slot` — the association this feature's new query joins through to reach `Session`/`Slot`. |

## New Repository Query

`BookingRepository.findByDoctorAndFixedTimeWindow(doctorProfileId, windowStart, windowEnd)` —
see research.md for the exact JPQL. Returns the trailing-window sample; the calculator derives
both sample size and no-show count from the single result list.

## New Class

`RiskBasedBufferSlotCalculator` (`com.cms.booking`, `@Component @Primary`, implements
`com.cms.scheduling.BufferSlotCalculator`):

```
calculateBufferSlotCount(session):
    window = [session.sessionDate - 90 days, session.sessionDate]
    bookings = findByDoctorAndFixedTimeWindow(session.doctorProfile.id, window.start, window.end)
    if bookings.size() < 5:
        return coldStartCalculator.calculateBufferSlotCount(session)  # delegates, returns 1
    noShowRate = count(b where b.slot.status == NO_SHOW) / bookings.size()
    percentage = min(noShowRate, 0.20)
    totalSlots = (session.endTime - session.startTime) / session.slotIntervalMinutes
    rawCount = round(percentage * totalSlots)
    return min(rawCount, 3)
```

No entity changes; `SlotGenerationService`'s existing call site
(`bufferSlotCalculator.calculateBufferSlotCount(session)`) and its existing even-distribution
logic (`computeEvenlySpacedIndices`) are untouched — Spring simply now resolves the
`BufferSlotCalculator` interface to this new `@Primary` bean instead of
`ColdStartBufferSlotCalculator` directly.
