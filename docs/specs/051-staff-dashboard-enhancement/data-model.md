# Data Model: Staff Operational Dashboard Enhancement

No new persisted entity, table, or column — this feature reads existing `Slot`/`Session` data only.

## New repository projection: `SlotRepository.SlotStatusCount`

Not persisted — an in-memory grouped-count projection, mirroring the existing `SlotRepository.SlotCountBySession` projection interface shape.

| Field | Type | Source |
|-------|------|--------|
| `status` | `SlotStatus` (`OPEN`/`BOOKED`/`COMPLETED`/`NO_SHOW`) | `Slot.status` |
| `count` | `long` | `COUNT(s)` grouped by `status`, scoped to one clinic + one date |

## New response DTO: `TodaySessionStatsResponse`

```java
public record TodaySessionStatsResponse(int completedCount, int noShowCount) {
    public static TodaySessionStatsResponse from(List<SlotRepository.SlotStatusCount> rows) { ... }
}
```

Built by folding the `SlotStatusCount` rows into 2 named counts (`COMPLETED`→`completedCount`, `NO_SHOW`→`noShowCount`), defaulting to 0 for a status with no matching group (a clinic with zero no-shows today produces no `NO_SHOW` row at all, per `GROUP BY` semantics — same "missing group = 0" handling `SessionSummaryResponse.from`'s `bookedSlotCount`/`totalSlotCount` null-coalescing already establishes for the sibling endpoint).

## Frontend: `TodaySessionStats` (API client type)

```ts
export interface TodaySessionStats {
  completedCount: number
  noShowCount: number
}
```

Fetched via a new `getTodayStats(clinicId, token)` in `frontend/src/features/day-sheet/api.ts` (same file as the existing `listSessions`/`getDaySheet`), mirroring their existing `fetch` + `DaySheetApiError` error-handling shape.

## "Today's sessions" list — no new frontend type

Reuses the existing `SessionSummary` type (`frontend/src/features/day-sheet/api.ts`) returned by the already-present `listSessions`, just read with a real page size instead of `size: 1`.
